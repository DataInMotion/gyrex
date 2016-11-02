/*******************************************************************************
 * Copyright (c) 2010, 2012 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.cloud.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.gyrex.boot.internal.app.ServerApplication;
import org.eclipse.gyrex.cloud.environment.INodeEnvironment;
import org.eclipse.gyrex.cloud.events.ICloudEventConstants;
import org.eclipse.gyrex.cloud.internal.zk.IZooKeeperLayout;
import org.eclipse.gyrex.cloud.internal.zk.ZooKeeperGate;
import org.eclipse.gyrex.cloud.internal.zk.ZooKeeperGateListener;
import org.eclipse.gyrex.cloud.internal.zk.ZooKeeperMonitor;
import org.eclipse.gyrex.cloud.internal.zk.ZooKeeperNodeInfo;
import org.eclipse.gyrex.server.Platform;
import org.eclipse.gyrex.server.internal.roles.LocalRolesManager;
import org.eclipse.gyrex.server.internal.roles.ServerRoleDefaultStartOption;
import org.eclipse.gyrex.server.internal.roles.ServerRoleDefaultStartOption.Trigger;
import org.eclipse.gyrex.server.internal.roles.ServerRolesRegistry;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.osgi.util.NLS;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.event.Event;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.data.Stat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures general state of the node in the cloud.
 * <p>
 * This class maintains the cloud membership of a node in a cluster. When active
 * it waits for ZooKeeperGate connection event. Once connected, it tries to
 * register the node using a generate node id.
 * </p>
 */
public class CloudState implements ZooKeeperGateListener {

	static final class AutoApproveJob extends Job {

		private final NodeInfo nodeInfo;

		/**
		 * Creates a new instance.
		 *
		 * @param nodeInfo
		 */
		public AutoApproveJob(final NodeInfo nodeInfo) {
			super("Node Auto-Approval Job");
			this.nodeInfo = nodeInfo;
			setSystem(true);
			setPriority(LONG);
		}

		@Override
		protected IStatus run(final IProgressMonitor monitor) {
			try {
				if (Boolean.parseBoolean(System.getenv("gyrex.autoapprove")) || Boolean.parseBoolean(System.getProperty("gyrex.autoapprove")) || getNodeEnvironment().inStandaloneMode()) {
					ZooKeeperNodeInfo.approve(nodeInfo.getNodeId(), null, nodeInfo.getLocation(), nodeInfo.getAddresses());
					if (getNodeEnvironment().inStandaloneMode()) {
						LOG.info("Node {} approved automatically. Welcome to your local cloud!", nodeInfo.getNodeId());
					} else {
						LOG.info("Node {} approved automatically. Because autoapprove was enabled.", nodeInfo.getNodeId());
					}
				}
				return Status.OK_STATUS;
			} catch (final Exception e) {
				LOG.error("Unable to automatically approve node {}. Please check server logs. {}", new Object[] { nodeInfo.getNodeId(), ExceptionUtils.getRootCauseMessage(e), e });
				return Status.CANCEL_STATUS;
			}
		}
	}

	private final class RegistrationJob extends Job implements ISchedulingRule {
		private static final int MAX_DELAY = 30000;
		private static final int MIN_DELAY = 1000;
		private volatile int delay = 0;

		public RegistrationJob() {
			super("Cloud Registration");
			setSystem(true);
			setRule(this);
			setPriority(SHORT);
		}

		@Override
		public boolean contains(final ISchedulingRule rule) {
			return rule == this;
		}

		@Override
		public boolean isConflicting(final ISchedulingRule rule) {
			// we conflict on the class name in order to also conflict with parallel versions
			// XXX this does not handle refactorings
			return rule.getClass().getName().equals(RegistrationJob.class.getName());
		}

		private void reschedule() {
			delay = Math.min(MAX_DELAY, delay > 0 ? delay * 2 : MIN_DELAY);
			LOG.warn("Node registration in cloud failed. Will retry in {} seconds.", delay / 1000);
			schedule(delay);
		}

		@Override
		protected IStatus run(final IProgressMonitor monitor) {
			if (monitor.isCanceled() || (getConnectState() != State.CONNECTING))
				return Status.CANCEL_STATUS;

			if (CloudDebug.cloudState) {
				LOG.debug("Attempting node registration in cloud.");
			}

			if (!registerWithCloud()) {
				// only re-schedule if state is still CONNECTING
				if (getConnectState() == State.CONNECTING) {
					reschedule();
				}
				return Status.CANCEL_STATUS;
			}

			if (CloudDebug.cloudState) {
				LOG.debug("Node registered in cloud!");
			}
			return Status.OK_STATUS;
		}
	}

	public static enum State {
		DISCONNECTED, CONNECTING, CONNECTED, CLOSED
	}

	private static final String SIGNATURE_SEPARATOR = "::";

	private static final Logger LOG = LoggerFactory.getLogger(CloudState.class);
	private static final AtomicReference<CloudState> instanceRef = new AtomicReference<CloudState>();

	static INodeEnvironment getNodeEnvironment() {
		return CloudActivator.getInstance().getNodeEnvironment();
	}

	/**
	 * Returns the node info for this node.
	 *
	 * @return the node info (maybe <code>null</code> if inactive)
	 */
	public static NodeInfo getNodeInfo() {
		final CloudState cloudState = instanceRef.get();
		if (cloudState == null)
			return null;

		return cloudState.myInfo.get();
	}

	/**
	 * Registers this node with the cloud.
	 */
	public static void registerNode() throws Exception {
		final CloudState cloudState = new CloudState();
		if (!instanceRef.compareAndSet(null, cloudState))
			throw new IllegalStateException("Already registered!");

		// hook with ZooKeeperGate
		ZooKeeperGate.addConnectionMonitor(cloudState);
		if (CloudDebug.cloudState) {
			LOG.debug("Initiated node registration with cloud.");
		}

		// immediately activate if possible
		try {
			cloudState.gateUp(ZooKeeperGate.get());
		} catch (final IllegalStateException ignored) {
			if (CloudDebug.cloudState) {
				LOG.debug("Deferring node registration due to inactive ZooKeeper gate.");
			}
		}
	}

	/**
	 * Unregisters the node from the cloud.
	 */
	public static void unregisterNode() {
		final CloudState cloudState = instanceRef.getAndSet(null);
		if (cloudState == null)
			throw new IllegalStateException("Not registered!");

		// remove from gate
		ZooKeeperGate.removeConnectionMonitor(cloudState);
		if (CloudDebug.cloudState) {
			LOG.debug("Initiated node un-registration from cloud.");
		}

		// de-activate if possible
		try {
			cloudState.gateDown(ZooKeeperGate.get());
		} catch (final IllegalStateException ignored) {
			if (CloudDebug.cloudState) {
				LOG.debug("No unregistration from cloud necessary because ZooKeeper gate is not active.");
			}
		}

		// set CLOSED
		cloudState.state.set(State.CLOSED);
	}

	private final Lock registrationLock = new ReentrantLock();
	private final AtomicReference<State> state = new AtomicReference<State>(State.DISCONNECTED);
	private final AtomicReference<NodeInfo> myInfo = new AtomicReference<NodeInfo>();
	private final AtomicReference<List<String>> myRoles = new AtomicReference<List<String>>();

	private final ZooKeeperMonitor monitor = new ZooKeeperMonitor() {
		@Override
		protected void pathCreated(final String path) {
			// check if active
			final NodeInfo nodeInfo = myInfo.get();
			if (nodeInfo == null)
				return;

			// process node activated events
			if (path.equals(IZooKeeperLayout.PATH_NODES_APPROVED.append(nodeInfo.getNodeId()).toString())) {
				if (CloudDebug.cloudState) {
					LOG.debug("Received path create event for approved node info. Assuming node has been approved.");
				}

				// refresh node info
				try {
					refreshNodeInfoAssumingApproved();
				} catch (final Exception e) {
					// smart updated failed, fallback to a complete reconnect
					if (CloudDebug.cloudState) {
						LOG.warn("Smart update failed for record change event ({}) for node {}, a complete re-connect will be forced! {}", new Object[] { path, nodeInfo, e.getMessage(), e });
					} else {
						LOG.warn("Smart update failed for record change event ({}) for node {}, a complete re-connect will be forced! {}", new Object[] { nodeInfo, e.getMessage() });
					}
					reconnect(nodeInfo);
				}
			}
		};

		@Override
		protected void pathDeleted(final String path) {
			// check if active
			final NodeInfo nodeInfo = myInfo.get();
			if (nodeInfo == null)
				return;

			// process node de-activation events
			if (path.equals(IZooKeeperLayout.PATH_NODES_APPROVED.append(nodeInfo.getNodeId()).toString())) {
				if (CloudDebug.cloudState) {
					LOG.debug("Received path delete event for approved node info. Assuming node has been revoked.");
				}

				// force re-connect
				reconnect(nodeInfo);
			}
		}

		private void reconnect(final NodeInfo nodeInfo) {
			try {
				unregisterNode();
			} catch (final Exception e) {
				if (CloudDebug.cloudState) {
					LOG.debug("Exception unregistering node {}. {}", new Object[] { nodeInfo, e.getMessage(), e });
				}
			}
			try {
				registerNode();
			} catch (final Exception e) {
				if (CloudDebug.debug) {
					LOG.error("Failed to re-register node. Node {} will not be available in cloud. {}", new Object[] { nodeInfo, e.getMessage(), e });
				} else {
					LOG.error("Failed to re-register node. Node {} will not be available in cloud. {}", new Object[] { nodeInfo, e.getMessage() });
				}
			}
		};

		@Override
		protected void recordChanged(final String path) {
			// check if active
			final NodeInfo nodeInfo = myInfo.get();
			if (nodeInfo == null)
				return;

			// process node updates
			if (path.equals(IZooKeeperLayout.PATH_NODES_APPROVED.append(nodeInfo.getNodeId()).toString())) {
				if (CloudDebug.cloudState) {
					LOG.debug("Received record change event for approved node info. Assuming node has been approved.");
				}

				// refresh node info
				try {
					refreshNodeInfoAssumingApproved();
				} catch (final Exception e) {
					// smart updated failed, fallback to a complete reconnect
					if (CloudDebug.cloudState) {
						LOG.warn("Smart update failed for record change event ({}) for node {}, a complete re-connect will be forced! {}", new Object[] { path, nodeInfo, e.getMessage(), e });
					} else {
						LOG.warn("Smart update failed for record change event ({}) for node {}, a complete re-connect will be forced! {}", new Object[] { nodeInfo, e.getMessage() });
					}
					reconnect(nodeInfo);
				}
			}
		}
	};

	/**
	 * Creates a new instance.
	 */
	private CloudState() {
	};

	/**
	 * Creates the ephemeral ALL node record which indicates that the node is
	 * online and does not clash with an existing node id.
	 *
	 * @param parentPath
	 * @param info
	 * @param signature
	 * @throws Exception
	 */
	private void createOrRestoreEphemeralNodeRecord(final IPath parentPath, final NodeInfo info, final String signature) throws Exception {
		// check for existing record
		final Stat existingStat = new Stat();
		final IPath nodePath = parentPath.append(info.getNodeId());
		final String existingSignature = ZooKeeperGate.get().readRecord(nodePath, (String) null, existingStat);

		// check that signature matches if one is present
		if ((null != existingSignature) && !existingSignature.equals(signature))
			// different non-null signature really means different node
			throw new IllegalStateException(String.format("Node id %s (path %s) already in use (reference %s)! Please investigate.", info.getNodeId(), parentPath.lastSegment(), existingSignature));

		// if existing signature maps it was created by us
		// check if the session is still the same
		if (existingStat.getEphemeralOwner() == ZooKeeperGate.get().getSessionId()) {
			// recover previous session
			if (CloudDebug.cloudState) {
				LOG.debug("Recovering previous session for node {} (path {}).", info.getNodeId(), parentPath.lastSegment());
			}
			return;
		}

		// created by a different session, so let's recover by removing what is there
		// and create a new node (see below)
		if (CloudDebug.cloudState) {
			LOG.debug("Cleaning up previous session for node {} (path {}).", info.getNodeId(), parentPath.lastSegment());
		}
		ZooKeeperGate.get().deletePath(nodePath);

		// create new record
		try {
			ZooKeeperGate.get().createPath(nodePath, CreateMode.EPHEMERAL, signature);
		} catch (final NodeExistsException e) {
			final String existingEntry = ZooKeeperGate.get().readRecord(nodePath, (String) null, null);
			throw new IllegalStateException(String.format("Node id %s (path %s) already in use (reference %s)! This may be a result of an unclean previous shutdown. Please retry and/or investigate if the problem still exists in a few seconds (depending on ZooKeeper session timeout).", info.getNodeId(), parentPath.lastSegment(), existingEntry));
		}
	}

	@Override
	public void gateDown(final ZooKeeperGate gate) {
		if (CloudDebug.cloudState) {
			LOG.debug("Received gate DOWN event. Current state is {}.", state.get());
		}

		// unregister completely
		unregisterFromCloud(false);
	}

	@Override
	public void gateRecovering(final ZooKeeperGate gate) {
		if (CloudDebug.cloudState) {
			LOG.debug("Received gate RECOVERING event. Current state is {}.", state.get());
		}

		// unregister partially
		unregisterFromCloud(true);
	}

	@Override
	public void gateUp(final ZooKeeperGate gate) {
		if (CloudDebug.cloudState) {
			LOG.debug("Received gate UP event. Current state is {}.", state.get());
		}

		// update state (but only if DISCONNECTED)
		if (state.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
			if (CloudDebug.cloudState) {
				LOG.debug("Scheduling cloud registration.");
			}

			// register asynchronously
			new RegistrationJob().schedule();
		}
	}

	private List<String> getCloudRolesToStart() {
		// get all roles that should be started
		final List<ServerRoleDefaultStartOption> startOptions = ServerRolesRegistry.getDefault().getAllDefaultStartOptions(Trigger.ON_CLOUD_CONNECT);

		// filter based on node filter
		final INodeEnvironment nodeEnvironment = getNodeEnvironment();
		for (final Iterator<ServerRoleDefaultStartOption> stream = startOptions.iterator(); stream.hasNext();) {
			final ServerRoleDefaultStartOption startOption = stream.next();
			final String nodeFilter = startOption.getNodeFilter();
			if (null != nodeFilter) {
				try {
					if (!nodeEnvironment.matches(nodeFilter)) {
						stream.remove();
					}
				} catch (final InvalidSyntaxException e) {
					stream.remove();
					LOG.error("Invalid node filter for start option of role {}. {}", startOption.getRoleId(), e.getMessage());
				}
			}
		}

		// sort according to start level
		Collections.sort(startOptions);

		// get role ids
		final List<String> roleIds = new ArrayList<String>(startOptions.size());
		for (final ServerRoleDefaultStartOption roleDefaultStart : startOptions) {
			final String roleId = roleDefaultStart.getRoleId();
			if (!roleIds.contains(roleId)) {
				roleIds.add(roleId);
			}
		}
		return roleIds;
	}

	State getConnectState() {
		return state.get();
	}

	/**
	 * Reads (or generates) a persistent node signature which does not change
	 * between sessions.
	 */
	private String getNodeSignature(final NodeInfo info) throws IOException {
		final File signatureFile = Platform.getStateLocation(CloudActivator.getInstance().getBundle()).append(".nodeCloudSignature").toFile();
		if (!signatureFile.exists()) {
			synchronized (CloudState.class) {
				if (!signatureFile.exists()) {
					FileUtils.write(signatureFile, info.getNodeId() + SIGNATURE_SEPARATOR + info.getLocation() + SIGNATURE_SEPARATOR + DigestUtils.shaHex(UUID.randomUUID().toString().getBytes(CharEncoding.US_ASCII)), CharEncoding.US_ASCII);
				}
			}
		}
		return FileUtils.readFileToString(signatureFile, CharEncoding.US_ASCII);
	}

	/**
	 * Initializes the node information.
	 */
	private NodeInfo initializeNodeInfo() throws Exception {
		final NodeInfo info = new NodeInfo();

		// calculate a node signature that is independent from the ZK session in order to determine ephemeral nodes from a previous session
		final String signature = getNodeSignature(info);

		// check for node id clashes by creating an ephemeral "all" record
		createOrRestoreEphemeralNodeRecord(IZooKeeperLayout.PATH_NODES_ALL, info, signature);

		// check if there is a recored in the "approved" list
		final NodeInfo approvedNodeInfo = readApprovedNodeInfo(info.getNodeId());
		if (approvedNodeInfo != null)
			return approvedNodeInfo;

		// create an ephemeral pending record
		// (and store the full node info in it)
		writePendingNodeRecord(info);

		return info;
	}

	/**
	 * Reads the approved node info from ZooKeeper and also sets a monitor to
	 * get information on update events.
	 *
	 * @return the read node info (maybe <code>null</code> if non found
	 * @throws Exception
	 *             in case an error occurred reading the node info
	 */
	NodeInfo readApprovedNodeInfo(final String nodeId) throws Exception {
		final IPath approvedNodeIdPath = IZooKeeperLayout.PATH_NODES_APPROVED.append(nodeId);
		final Stat stat = new Stat();

		// check existence in a loop which ensures that we catch deletions in progress
		// this also sets a watch in case the path must be created first
		int attempts = 0;
		while (ZooKeeperGate.get().exists(approvedNodeIdPath, monitor)) {
			try {
				// read record
				// (note, we don't set a monitor here, it's already set during the #exists call)
				final byte[] record = ZooKeeperGate.get().readRecord(approvedNodeIdPath, stat);

				// return node info if available
				if (record != null)
					return new NodeInfo(new ZooKeeperNodeInfo(nodeId, true, record, stat.getVersion()));
			} catch (final NoNodeException e) {
				// someone deleted the path in between
				attempts++;
				if (attempts > 5)
					throw new IllegalStateException("Failed read approved node info. The exists call succeeds but the read call says it doesn't exist. Please verify the ZooKeeper state.");
			}
		}

		return null;
	}

	/**
	 * Smart refresh of the approved node information.
	 * <p>
	 * This reads the new information from ZooKeeper and performs the necessary
	 * updates.
	 * </p>
	 *
	 * @throws Exception
	 *             in case an error occurred refreshing the node info
	 */
	void refreshNodeInfoAssumingApproved() throws Exception {
		registrationLock.lock();
		try {
			final NodeInfo oldInfo = myInfo.get();
			if (oldInfo == null)
				throw new IllegalStateException("cloud is inactive");

			// read info
			final NodeInfo newInfo = readApprovedNodeInfo(oldInfo.getNodeId());
			if (newInfo == null)
				throw new IllegalStateException("no approved node info found");
			if (!StringUtils.equals(oldInfo.getNodeId(), newInfo.getNodeId()))
				throw new IllegalStateException("node id mismatch");

			// check version
			if (oldInfo.getVersion() >= newInfo.getVersion()) {
				if (CloudDebug.cloudState) {
					LOG.debug("Already up to date (current {} >= remote {})", oldInfo, newInfo);
				}
				return;
			}

			// log message
			if (CloudDebug.cloudState) {
				LOG.debug("Updating node info to {}", newInfo);
			}

			// set node info
			myInfo.set(newInfo);

			// set online if approved
			if (newInfo.isApproved()) {
				setNodeOnline(newInfo);
			}
		} finally {
			registrationLock.unlock();
		}
	}

	private void refreshServerRoles() {
		// get roles that should be started
		final List<String> rolesToStart = getCloudRolesToStart();

		// set new roles
		final List<String> alreadyStartedRoles = myRoles.getAndSet(rolesToStart);

		// find roles to stop
		if (null != alreadyStartedRoles) {
			final List<String> toStop = new ArrayList<String>(alreadyStartedRoles.size());
			for (final String roledId : alreadyStartedRoles) {
				if (!rolesToStart.contains(roledId)) {
					toStop.add(roledId);
				}
			}
			// stop in reverse order
			Collections.reverse(toStop);
			LocalRolesManager.deactivateRoles(toStop);
		}

		// find new roles to start
		final List<String> toStart = new ArrayList<String>(rolesToStart.size());
		for (final String roledId : rolesToStart) {
			if ((null == alreadyStartedRoles) || !alreadyStartedRoles.contains(roledId)) {
				toStart.add(roledId);
			}
		}
		LocalRolesManager.activateRoles(toStart);
	}

	/**
	 * Attempts registering the node in the cloud.
	 * <p>
	 * Called asynchronously after {@link #gateUp(ZooKeeperGate)} via
	 * {@link RegistrationJob}.
	 * </p>
	 *
	 * @return <code>true</code> on success, <code>false</code> otherwise
	 */
	boolean registerWithCloud() {
		// synchronize in order to prevent concurrent registrations
		registrationLock.lock();
		try {
			final State connectState = getConnectState();
			if (connectState != State.CONNECTING) {
				// abort when state is not connecting
				if (CloudDebug.debug) {
					LOG.debug("Aborting cloud registration due to invalid state ({}).", connectState);
				}
				return false;
			}

			// load / create our node info
			NodeInfo nodeInfo = null;
			try {
				nodeInfo = initializeNodeInfo();
				LOG.info("Node {} initialized.", nodeInfo);
			} catch (final Exception e) {
				// log error
				LOG.error("Unable to initialize node info. Node will not be available in cloud. {}", new Object[] { ExceptionUtils.getRootCauseMessage(e), e });
				return false;
			}

			// set node info
			myInfo.set(nodeInfo);

			// set node online (if approved)
			try {
				if (nodeInfo.isApproved()) {
					setNodeOnline(nodeInfo);
				} else if (Boolean.parseBoolean(System.getenv("gyrex.autoapprove")) || Boolean.parseBoolean(System.getProperty("gyrex.autoapprove")) || (Platform.inDevelopmentMode() && getNodeEnvironment().inStandaloneMode())) {
					// auto-approve in standalone mode or if the autoapprove option is set
					// this needs to be async because approval triggers ZK events on which we react
					LOG.info("Attempting automatic approval of node {} in standalong development system.", nodeInfo);
					new AutoApproveJob(nodeInfo).schedule(500l);
				} else {
					LOG.info("Node {} needs to be approved first. Please contact cloud administrator with node details for approval.", nodeInfo);
				}
			} catch (final Exception e) {
				if (CloudDebug.debug) {
					LOG.error("Unable to set node to online. Node will not be available in cloud.", e);
				} else {
					LOG.error("Unable to set node to online. Node will not be available in cloud. {}", e.getMessage());
				}

				return false;
			}

			// set connected
			state.set(State.CONNECTED);

			// successful registration at this point
			return true;
		} finally {
			registrationLock.unlock();
		}
	}

	private void sendNodeEvent(final NodeInfo node, final String topic) {
		if (CloudDebug.cloudState) {
			LOG.debug("Node {} sending event {}.", node, topic);
		}
		try {
			final Map<String, Object> properties = new HashMap<String, Object>(3);
			properties.put(ICloudEventConstants.NODE_ID, node.getNodeId());
			properties.put(ICloudEventConstants.NODE_TAGS, node.getTags());
			properties.put("node.name", node.getName());
			properties.put("node.location", node.getLocation());
			final Event event = new Event(topic, properties);
			if (CloudDebug.cloudState) {
				LOG.debug("Sending node event: {}", event);
			}
			CloudActivator.getInstance().getEventAdmin().sendEvent(event);
		} catch (final Exception e) {
			LOG.error("Error while notifying public cloud node listeners. {}", new Object[] { e.getMessage(), e });
		}
	}

	private void setNodeOffline(final NodeInfo node, final boolean interruptOnly) throws Exception {
		// send offline event (but only if the node was approved)
		if (node.isApproved()) {
			sendNodeEvent(node, interruptOnly ? ICloudEventConstants.TOPIC_NODE_INTERRUPTED : ICloudEventConstants.TOPIC_NODE_OFFLINE);
		}

		// de-activate all roles (if not interrupted)
		if (!interruptOnly) {
			final List<String> roles = myRoles.getAndSet(null);
			if ((null != roles) && !roles.isEmpty()) {
				Collections.reverse(roles); // de-activate in reverse order
				LocalRolesManager.deactivateRoles(roles);
			}
		}

		// stop cloud services (only on full offline)
		if (!interruptOnly) {
			CloudActivator.getInstance().stopCloudServices();
		}

		// stop node metrics reporter
		NodeMetricsReporter.stop();

		// remove an ephemeral records for this node (but only if not interrupted)
		if (!interruptOnly) {
			try {
				ZooKeeperGate.get().deletePath(IZooKeeperLayout.PATH_NODES_ONLINE.append(node.getNodeId()));
				ZooKeeperGate.get().deletePath(IZooKeeperLayout.PATH_NODES_ALL.append(node.getNodeId()));
			} catch (final IllegalStateException e) {
				// gate is gone
				if (CloudDebug.cloudState) {
					LOG.debug("Ignored exception while removing ephemeral nodes.", e);
				}
			} catch (final ConnectionLossException e) {
				// connection is gone
				if (CloudDebug.cloudState) {
					LOG.debug("Ignored exception while removing ephemeral nodes.", e);
				}
			} catch (final SessionExpiredException e) {
				// session is gone
				if (CloudDebug.cloudState) {
					LOG.debug("Ignored exception while removing ephemeral nodes.", e);
				}
			} catch (final Exception e) {
				throw e;
			}
		}

		// log message (only on success)
		LOG.info("Node {} is now {}.", node, interruptOnly ? "interrupted" : "offline");
	}

	private void setNodeOnline(final NodeInfo node) throws Exception {
		if (!node.isApproved())
			throw new IllegalArgumentException(NLS.bind("Node {0} must be approved first before it is allowed to join the cloud.", node.getNodeId()));

		// create an ephemeral record for this node in the "online" tree
		createOrRestoreEphemeralNodeRecord(IZooKeeperLayout.PATH_NODES_ONLINE, node, getNodeSignature(node));

		// start node metrics publisher (cpu load, memory resources)
		// (disabled for now, we better have to understand the use cases and ZooKeeper impacts)
		//NodeMetricsReporter.start();

		// start cloud services
		CloudActivator.getInstance().startCloudServices(node);

		// refresh cloud roles
		refreshServerRoles();

		// send node activation event
		sendNodeEvent(node, ICloudEventConstants.TOPIC_NODE_ONLINE);

		// log success
		LOG.info("Node {} is now online.", node);
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("CloudState [").append(state.get()).append(", ").append(myInfo.get()).append("]");
		return builder.toString();
	}

	/**
	 * Unregisters the node from the cloud. *
	 * <p>
	 * Called synchronously from {@link #gateDown(ZooKeeperGate)} or
	 * {@link #gateRecovering(ZooKeeperGate)}.
	 * </p>
	 *
	 * @param interruptOnly
	 */
	void unregisterFromCloud(final boolean interruptOnly) {
		final State oldState = state.getAndSet(State.DISCONNECTED);
		if (oldState == State.DISCONNECTED) {
			// already disconnected
			if (CloudDebug.cloudState) {
				LOG.debug("Ignoring disconnect request for already disconnected node.");
			}
			return;
		}

		if (CloudDebug.cloudState) {
			LOG.debug("Attempting node de-registration from cloud.", new Exception("Node Disconnect Call Stack"));
		}

		// synchronize in order to prevent concurrent registrations
		registrationLock.lock();
		try {
			// get current node info
			final NodeInfo nodeInfo = myInfo.getAndSet(null);
			if (nodeInfo == null)
				// already unregistered
				return;

			// set node completely offline
			try {
				setNodeOffline(nodeInfo, interruptOnly);
			} catch (final Exception e) {
				// this may happen (eg. connection lost unintentionally)
				// only log a warning only if this happen during regular life-time
				if (ServerApplication.isRunning()) {
					LOG.warn("Unable to set node offline. You may need to check node directory manually in order to bring the node back online. Another option would be complete shutdown and re-start after a few seconds. {}", ExceptionUtils.getRootCauseMessage(e), e);
				} else if (CloudDebug.cloudState) {
					LOG.debug("Exception while setting node offline. {}", ExceptionUtils.getRootCauseMessage(e), e);
				}
			}
		} finally {
			registrationLock.unlock();
		}
	}

	/**
	 * Writes the node info to the pending node record as ephemeral node.
	 */
	private void writePendingNodeRecord(final NodeInfo info) throws Exception {
		final IPath pendingNodeIdPath = IZooKeeperLayout.PATH_NODES_PENDING.append(info.getNodeId());

		// check existence in a loop which ensures that we catch deletions in progress
		int attempts = 0;
		while (ZooKeeperGate.get().exists(pendingNodeIdPath)) {
			try {
				// delete record
				ZooKeeperGate.get().deletePath(pendingNodeIdPath);
			} catch (final NoNodeException e) {
				// someone deleted the path in between
				attempts++;
				if (attempts > 5)
					throw new IllegalStateException("Failed read approved node info. The exists call succeeds but the read call says it doesn't exist. Please verify the ZooKeeper state.");
			}
		}

		// create new info
		final ZooKeeperNodeInfo nodeInfo = new ZooKeeperNodeInfo(info.getNodeId(), true, null, 0);
		nodeInfo.setName(info.getName());
		nodeInfo.setLocation(info.getLocation());
		if (!info.getAddresses().isEmpty()) {
			nodeInfo.setAddresses(info.getAddresses());
		}
		if (!info.getTags().isEmpty()) {
			nodeInfo.setTags(info.getTags());
		}

		// create new record
		ZooKeeperNodeInfo.save(nodeInfo, false);
	}
}

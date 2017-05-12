[![Build Status](https://travis-ci.org/eclipse/gyrex.svg?branch=master)](https://travis-ci.org/eclipse/gyrex)

The Eclipse Gyrex Project
=========================

Gyrex is a scalable server platform based on OSGi. The Gyrex Kernel runs on
the Equinox OSGi framework and integrates with Apache ZooKeeper for building
connected instances.

Among other things Gyrex provides:
- OSGi cluster management and coordination via Apache ZooKeeper
- Easy administration using a feature-rich web interface
- Collecting of runtime metrics and publishing of diagnostics information
- Integration with logging apis to simplify log management for your applications
- Software provisioning within the cluster using p2 (dynamic updates)
- A multi-tenancy runtime for executing logic in a per-tenant context
  (eg., run multiple instance of the same web app code base for different
   tenent urls/domains in different configurations)


Checkout our homepage at [eclipse.org/gyrex/](https://www.eclipse.org/gyrex/)

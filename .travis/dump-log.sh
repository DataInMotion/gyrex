#!/bin/bash

echo
echo
eval printf %.0s- '{1..'"${COLUMNS:-$(tput cols)}"\}; echo
echo 👀  $1
echo
cat $1
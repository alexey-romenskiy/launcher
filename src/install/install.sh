#!/bin/bash

DIR=`pwd`
rm -rf $HOME/launcher
mkdir $HOME/launcher
mv bin $HOME/launcher/bin
mv lib $HOME/launcher/lib
mkdir $HOME/launcher/jdk
tar xzpf openjdk-17.0.2-linux_x64.tar.gz -C $HOME/launcher/jdk/
cd
grep -q 'export PATH="$PATH:$HOME/launcher/bin"' .bash_profile
if [ $? -ne 0 ] ; then
  echo >> .bash_profile
  echo 'export PATH="$PATH:$HOME/launcher/bin"' >> .bash_profile
fi
rm -rf $DIR

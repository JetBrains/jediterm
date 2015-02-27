#!/bin/sh

java -cp "lib/pty4j-0.3.jar:lib/guava-14.0.1.jar:lib/jna.jar:lib/jsch-0.1.51.jar:lib/junit-4.10.jar:lib/jzlib-1.1.1.jar:lib/log4j-1.2.14.jar:lib/purejavacomm-0.0.17.jar:build/jediterm-pty-2.0.jar" com.jediterm.pty.PtyMain 

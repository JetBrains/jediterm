JediTerm
========

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

[![Build Status](https://travis-ci.org/JetBrains/jediterm.png?branch=master)](https://travis-ci.org/JetBrains/jediterm)


The main purpose of the project is to provide a pure Java terminal widget that can be easily embedded 
into an IDE.
It supports terminal sessions both for SSH connections and local PTY on Mac OSX, Linux and Windows.


The library is used by JetBrains IDEs like PyCharm, IDEA, PhpStorm, WebStorm, AppCode, CLion, and Rider.

Since version 2.5 there is a standalone version of the JediTerm terminal, provided as Mac OSX distribution.


The name JediTerm origins from J(from `Java`) + edi(reversed `IDE`) + Term(obviously from `terminal`).
Also the word Jedi itself gives some confidence and hope in the Universe of thousands of different terminal implementations.


Run
-------

To run the standalone JediTerm terminal from sources just execute _jediterm.sh_ or _jediterm.bat_.
Or use the binary distribution from the [Releases](https://github.com/JetBrains/jediterm/releases/) page.



Build
-----

Gradle is used to build this project. The project consists of 4 sub-projects:
* **terminal**

    The core library that provides VT100 compatible terminal emulator and Java Swing based implementation of terminal panel UI.

* **ssh**

    The jediterm-ssh.jar library that provides, using the Jsch library, a terminal for remote SSH terminal sessions.

* **pty**

    The jediterm-pty.jar library that, by using the [Pty4J](https://github.com/traff/pty4j) library, enables a terminal for local PTY terminal sessions.

* **JediTerm**

    The standalone version of the JediTerm terminal distributed as a .dmg for Mac OSX.


Features
--------
* Ssh using JSch from jcraft.org
* Local terminal for Unix, Mac and Windows using [Pty4J](https://github.com/traff/pty4j)
* Xterm emulation - passes most of tests from vttest
* Xterm 256 colours
* Scrolling
* Copy/Paste
* Mouse support
* Terminal resizing from client or server side
* Terminal tabs



Authors
-------
Dmitry Trofimov <dmitry.trofimov@jetbrains.com>, Clément Poulain



Links
-----
 * Terminal protocol description: http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 * Terminal Character Set Terminology and Mechanics: http://www.columbia.edu/kermit/k95manual/iso2022.html
 * VT420 Programmer Reference Manual: http://manx.classiccmp.org/collections/mds-199909/cd3/term/vt420rm2.pdf
 * Pty4J library: https://github.com/traff/pty4j
 * JSch library: http://www.jcraft.com/jsch
 * UTF8 Demo: http://www.cl.cam.ac.uk/~mgk25/ucs/examples/UTF-8-demo.txt
 * Control sequences visualization: http://www.gnu.org/software/teseq/
 * Terminal protocol tests: http://invisible-island.net/vttest/



Open Source Origin and History
------
The initial version of the JediTerm was a reworked terminal emulator Gritty, which was in it's own turn a reworked JCTerm 
terminal implementation. Now there is nothing in the source code left from Gritty and JCTerm. Everything was 
rewritten from scratch. A lot of new features were added.

Character sets designation and mapping implementation is based on
respective classes from jVT220 (https://github.com/jawi/jVT220, Apache 2.0 licensed) by J.W. Janssen.


Standalone distribution relies heavily on customized Swing UI widgets taken from IntelliJ Community platform repository
(https://github.com/JetBrains/intellij-community) by JetBrains.


Licenses
-------
All sources in the repository are licensed under LGPLv3, except the following roots, which are Apache 2.0 licensed:
* JediTerm/*
* terminal/src/com/jediterm/terminal/emulator/*

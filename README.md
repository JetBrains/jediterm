JediTerm
========

[![Build Status](https://travis-ci.org/JetBrains/jediterm.png?branch=master)](https://travis-ci.org/JetBrains/jediterm)

The main purpose of the project is to provide a pure Java terminal widget ready
to embed into IDE. From this comes the name
J(from `Java`)edi(reversed `IDE`)Term(obviously from `terminal`)


Authors
-------
Dmitry Trofimov <dmitry.trofimov@jetbrains.com>, Clément Poulain


License
-------
Licensed under LGPL.


Features
--------

* Ssh using JSch from jcraft.org 
* Local terminal using Pty4J
* Xterm emulation
* Xterm 256 colours
* Scrolling
* Copy/Paste
* Mouse support
* Terminal resizing from client or server side
* Terminal tabs 


JediTerm 2.0
------------
* Refactoring of tabs
* Memory consumption optimization - don't store bitmaps in memory, paint from text
* Double-width symbols support - get rid of back-buffer


Links
-----
 * Terminal protocol description: http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 * Terminal Character Set Terminology and Mechanics: http://www.columbia.edu/kermit/k95manual/iso2022.html
 * VT420 Programmer Reference Manual: http://manx.classiccmp.org/collections/mds-199909/cd3/term/vt420rm2.pdf
 * Pty4J library: https://github.com/traff/pty4j
 * JSch library: http://www.jcraft.com/jsch
 * UTF8 Demo: http://www.cl.cam.ac.uk/~mgk25/ucs/examples/UTF-8-demo.txt
 * Control sequences visualization: http://www.gnu.org/software/teseq/



Origin
------
Continued as a fork of Gritty (http://code.google.com/p/gritty) by Robert Wittams
and Omer Kudat, which was in its own turn a rework of
JCTerm(http://www.jcraft.com/jcterm) by Atsuhiko Yamanaka

Character sets designation and mapping implementation is based on 
respective classes from jVT220 (https://github.com/jawi/jVT220, Apache 2.0 licensed) by J.W. Janssen

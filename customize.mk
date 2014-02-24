# Copyright (C) 2011 jOVAL.org.  All rights reserved.
# This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt
#
# INSTRUCTIONS:
#
# This file is intended to be modified to suit your build environment.
# $(TOP) is defined to be this directory -- the top-level directory for the jOVAL source tree.
#

#
# JAVA_HOME is where you've installed your JDK.
#
#JAVA_HOME=$(TOP)/../../tools/jdk1.7.0_21
JAVA_HOME=$(TOP)/../../tools/jdk1.6.0_26

#
# JAXB_HOME is where you've installed JAXB.
# Note: This is required when specifying a pre-Java7 JDK in JAVA_HOME.
#
JAXB_HOME=$(TOP)/../../tools/jaxb-ri-2.2.6

#
# JRE[ARCH]_HOME is the install path for the JRE that will be bundled with the distribution for
# architecture ARCH.
#
JRE32_HOME=$(TOP)/../../tools/jre170_21_x86
JRE64_HOME=$(TOP)/../../tools/jre170_21_x64

#
# JSAF_HOME is the location of the jSAF FOSS git repository.
# NOTE: Use the 1.1.2 branch
#
# See http://github.org/joval/jSAF
#
JSAF_HOME=$(TOP)/../jSAF

#
# JPE_HOME is the location of the jPE FOSS git repository.
# See http://github.org/joval/jPE
#
JPE_HOME=$(TOP)/../jPE

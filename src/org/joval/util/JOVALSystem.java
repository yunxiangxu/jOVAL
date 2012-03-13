// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Timer;
import java.util.Vector;

import ch.qos.cal10n.IMessageConveyor;
import ch.qos.cal10n.MessageConveyor;
import ch.qos.cal10n.MessageConveyorException;
import org.slf4j.cal10n.LocLogger;
import org.slf4j.cal10n.LocLoggerFactory;

import org.joval.intf.oval.IDefinitionFilter;
import org.joval.intf.oval.IDefinitions;
import org.joval.intf.oval.IEngine;
import org.joval.intf.oval.IVariables;
import org.joval.intf.plugin.IPlugin;
import org.joval.intf.system.IBaseSession;
import org.joval.intf.util.ILoggable;
import org.joval.intf.util.IProperty;
import org.joval.oval.DefinitionFilter;
import org.joval.oval.Definitions;
import org.joval.oval.OvalException;
import org.joval.oval.Variables;
import org.joval.oval.engine.Engine;

/**
 * This class is used to retrieve JOVAL-wide resources, like SLF4J-based logging, cal10n-based messages and jOVAL and OVAL data
 * model properties and object factories.
 *
 * It is also the primary entry-point into the jOVAL SDK, and can be used to create and configure an Engine, and set properties
 * that affect the behavior of the product.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class JOVALSystem {
    /**
     * Property indicating the package names for classes in the OVAL (Open Vulnerability and Assessment Language)
     * definitions schema.
     */
    public static final String OVAL_PROP_DEFINITIONS = "oval.definitions.packages";

    /**
     * Property indicating the package names for classes in the OVAL (Open Vulnerability and Assessment Language)
     * results schema.
     */
    public static final String OVAL_PROP_RESULTS = "oval.results.packages";

    /**
     * Property indicating the package names for classes in the OVAL (Open Vulnerability and Assessment Language)
     * system characteristics schema.
     */
    public static final String OVAL_PROP_SYSTEMCHARACTERISTICS = "oval.systemcharacteristics.packages";

    /**
     * Property indicating the package names for classes in the OVAL (Open Vulnerability and Assessment Language)
     * variables schema.
     */
    public static final String OVAL_PROP_VARIABLES = "oval.variables.packages";

    /**
     * Property indicating the package names for classes in the OVAL (Open Vulnerability and Assessment Language)
     * evaluation-id schema.
     */
    public static final String OVAL_PROP_EVALUATION_ID = "oval.evaluation-id.packages";

    /**
     * Property indicating the package names for classes in the OVAL (Open Vulnerability and Assessment Language)
     * directives schema.
     */
    public static final String OVAL_PROP_DIRECTIVES = "oval.directives.packages";

    /**
     * Property indicating the package names for classes in the XCCDF (eXtensible Configuration Checklist Description Format)
     * schema.
     */
    public static final String XCCDF_PROP_PACKAGES = "xccdf.packages";

    /**
     * Property indicating the package names for classes in the CPE (Common Platform Enumeration) schema.
     */
    public static final String CPE_PROP_PACKAGES = "cpe.packages";

    /**
     * Property indicating the package names for classes in the SVRL (Schematron Validation Report Language) schema.
     */
    public static final String SVRL_PROP_PACKAGES = "svrl.packages";

    /**
     * Property indicating the product name.
     */
    public static final String SYSTEM_PROP_PRODUCT = "productName";

    /**
     * Property indicating the product version.
     */
    public static final String SYSTEM_PROP_VERSION = "version";

    /**
     * Property indicating the product build date.
     */
    public static final String SYSTEM_PROP_BUILD_DATE = "build.date";

    /**
     * A data structure providing easy access to the OVAL schema object factories.
     */
    public static final Factories factories = new Factories();

    private static final String SYSTEM_SECTION	= JOVALSystem.class.getName();
    private static final String CONFIG_RESOURCE	= "defaults.ini";
    private static final String OVAL_RESOURCE	= "oval.properties";
    private static final String CPE_RESOURCE	= "cpe.properties";
    private static final String XCCDF_RESOURCE	= "xccdf.properties";
    private static final String SVRL_RESOURCE	= "svrl.properties";

    private static Timer timer;
    private static IMessageConveyor mc;
    private static LocLoggerFactory loggerFactory;
    private static LocLogger sysLogger;
    private static Properties schemaProps;
    private static IniFile config;

    static {
	timer = new Timer("jOVAL system timer", true);
	mc = new MessageConveyor(Locale.getDefault());
	try {
	    //
	    // Get a message to test whether localized messages are available for the default Locale
	    //
	    getMessage(JOVALMsg.ERROR_EXCEPTION);
	} catch (MessageConveyorException e) {
	    //
	    // The test failed, so set the message Locale to English
	    //
	    mc = new MessageConveyor(Locale.ENGLISH);
	}
	loggerFactory = new LocLoggerFactory(mc);
	sysLogger = loggerFactory.getLocLogger(JOVALSystem.class);
	config = new IniFile();
	schemaProps = new Properties();
	try {
	    ClassLoader cl = Thread.currentThread().getContextClassLoader();

	    InputStream rsc = cl.getResourceAsStream(CONFIG_RESOURCE);
	    if (rsc == null) {
		sysLogger.warn(getMessage(JOVALMsg.ERROR_MISSING_RESOURCE, CONFIG_RESOURCE));
	    } else {
		config.load(rsc);
	    }

	    rsc = cl.getResourceAsStream(OVAL_RESOURCE);
	    if (rsc == null) {
		sysLogger.warn(getMessage(JOVALMsg.ERROR_MISSING_RESOURCE, OVAL_RESOURCE));
	    } else {
		schemaProps.load(rsc);
	    }

	    rsc = cl.getResourceAsStream(CPE_RESOURCE);
	    if (rsc == null) {
		sysLogger.debug(getMessage(JOVALMsg.ERROR_MISSING_RESOURCE, CPE_RESOURCE));
	    } else {
		schemaProps.load(rsc);
	    }

	    rsc = cl.getResourceAsStream(XCCDF_RESOURCE);
	    if (rsc == null) {
		sysLogger.debug(getMessage(JOVALMsg.ERROR_MISSING_RESOURCE, XCCDF_RESOURCE));
	    } else {
		schemaProps.load(rsc);
	    }

	    rsc = cl.getResourceAsStream(SVRL_RESOURCE);
	    if (rsc == null) {
		sysLogger.debug(getMessage(JOVALMsg.ERROR_MISSING_RESOURCE, SVRL_RESOURCE));
	    } else {
		schemaProps.load(rsc);
	    }
	} catch (IOException e) {
	    sysLogger.error(getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	}
    }

    /**
     * Return a directory suitable for storing transient application data, like state information that may persist
     * between invocations.  This is either a directory called .jOVAL beneath the user's home directory, or on Windows,
     * it will be a directory named jOVAL in the appropriate AppData storage location.
     */
    public static File getDataDirectory() {
	File dataDir = null;
	if (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
	    String s = System.getenv("LOCALAPPDATA");
	    if (s == null) {
		s = System.getenv("APPDATA");
	    }
	    if (s != null) {
		File appDataDir = new File(s);
		dataDir = new File(appDataDir, "jOVAL");
	    }
	}
	if (dataDir == null) {
	    File homeDir = new File(System.getProperty("user.home"));
	    dataDir = new File(homeDir, ".jOVAL");
	}
	return dataDir;
    }

    /**
     * Retrieve a localized String, given the key and substitution arguments.
     */
    public static String getMessage(JOVALMsg key, Object... args) {
	return mc.getMessage(key, args);
    }

    /**
     * Retrieve the daemon Timer used for scheduled jOVAL tasks.
     */
    public static Timer getTimer() {
	return timer;
    }

    /**
     * Retrieve the default localized system logger used by the jOVAL library.
     */
    public static LocLogger getLogger() {
	return sysLogger;
    }

    /**
     * Retrieve/create a localized jOVAL logger with a particular name.  This is useful for passing to an IPlugin, if you
     * want all of the plugin's log messages routed to a specific logger.
     */
    public static LocLogger getLogger(String name) {
	return loggerFactory.getLocLogger(name);
    }

    /**
     * Overlay sectioned jOVAL system configuration parameters from a file.
     *
     * jOVAL is equipped with a default configuration that is loaded by this class's static initializer. When using
     * this method to override the defaults, make sure to do so prior to the creation of any session objects.
     */
    public static void addConfiguration(File f) throws IOException {
	sysLogger.info(JOVALMsg.STATUS_CONFIG_OVERLAY, f.getPath());
	config.load(f);
    }

    /**
     * Configure a session in accordance with the jOVAL system configuration.
     */
    public static void configureSession(IBaseSession session) {
	List<Class> visited = new Vector<Class>();
	for (Class clazz : session.getClass().getInterfaces()) {
	    configureInterface(clazz, session.getProperties(), visited, session.getClass().getName());
	}
	Class clazz = session.getClass().getSuperclass();
	while(clazz != null) {
	    for (Class intf : clazz.getInterfaces()) {
		if (!visited.contains(intf)) {
		    configureInterface(intf, session.getProperties(), visited, session.getClass().getName());
		}
	    }
	    clazz = clazz.getSuperclass();
	}
    }

    /**
     * Retrieve an OVAL system property.
     *
     * @param key specify one of the PROP_* keys
     */
    public static String getSystemProperty(String key) {
	try {
	    return config.getProperty(SYSTEM_SECTION, key);
	} catch (NoSuchElementException e) {
	}
	return null;
    }

    /**
     * Retrieve an OVAL schema property.
     *
     * @param name specify one of the OVAL_PROP_*, CPE_PROP_* or XCCDF_PROP_* keys
     */
    public static String getSchemaProperty(String name) {
	return schemaProps.getProperty(name);
    }

    /**
     * Create an IDefinitionFilter based on the supplied File, which should conform to the evaluation-ids schema.
     *
     * @throws OvalException if there was an error, such as the file not conforming to the schema.
     */
    public static final IDefinitionFilter createDefinitionFilter(File f) throws OvalException {
	return new DefinitionFilter(f);
    }

    /**
     * Create an IDefinitionFilter that will accept only IDs in the supplied collection.
     */
    public static final IDefinitionFilter createAcceptFilter(Collection<String> ids) {
	return new DefinitionFilter(ids);
    }

    /**
     * Create an IVariables based on the supplied File, which should conform to the OVAL variables schema.
     */
    public static final IVariables createVariables(File f) throws OvalException {
	return new Variables(f);
    }

    /**
     * Create an IDefinitions index for an XML file containing OVAL definitions.
     */
    public static final IDefinitions createDefinitions(File f) throws OvalException {
	return new Definitions(f);
    }

    /**
     * Create an engine for evaluating OVAL definitions using a plugin.  A null plugin value can be specified if an
     * ISystemCharacteristics (or a system-characteristics.xml file) is set before running.
     */
    public static final IEngine createEngine(IPlugin plugin) {
	return new Engine(plugin);
    }

    //
    // Simplify access to all the OVAL Object Factories
    //

    /**
     * A data structure containing fields and structures for accessing all the OVAL object factories.
     */
    public static class Factories {
	public oval.schemas.common.ObjectFactory common;
	public oval.schemas.directives.core.ObjectFactory directives;
	public oval.schemas.evaluation.id.ObjectFactory evaluation;
	public oval.schemas.results.core.ObjectFactory results;
	public oval.schemas.variables.core.ObjectFactory variables;
	public DefinitionFactories definitions;
	public SystemCharacteristicsFactories sc;

	Factories() {
	    common = new oval.schemas.common.ObjectFactory();
	    definitions = new DefinitionFactories();
	    directives = new oval.schemas.directives.core.ObjectFactory();
	    evaluation = new oval.schemas.evaluation.id.ObjectFactory();
	    results = new oval.schemas.results.core.ObjectFactory();
	    sc = new SystemCharacteristicsFactories();
	    variables = new oval.schemas.variables.core.ObjectFactory();
	}
    }

    /**
     * A data structure containing fields for all the OVAL definition object factories.
     */
    public static class DefinitionFactories {
	public oval.schemas.definitions.aix.ObjectFactory aix;
	public oval.schemas.definitions.apache.ObjectFactory apache;
	public oval.schemas.definitions.catos.ObjectFactory catos;
	public oval.schemas.definitions.core.ObjectFactory core;
	public oval.schemas.definitions.esx.ObjectFactory esx;
	public oval.schemas.definitions.freebsd.ObjectFactory freebsd;
	public oval.schemas.definitions.hpux.ObjectFactory hpux;
	public oval.schemas.definitions.independent.ObjectFactory independent;
	public oval.schemas.definitions.ios.ObjectFactory ios;
	public oval.schemas.definitions.linux.ObjectFactory linux;
	public oval.schemas.definitions.macos.ObjectFactory macos;
	public oval.schemas.definitions.pixos.ObjectFactory pixos;
	public oval.schemas.definitions.sharepoint.ObjectFactory sharepoint;
	public oval.schemas.definitions.solaris.ObjectFactory solaris;
	public oval.schemas.definitions.unix.ObjectFactory unix;
	public oval.schemas.definitions.windows.ObjectFactory windows;

	private DefinitionFactories() {
	    aix = new oval.schemas.definitions.aix.ObjectFactory();
	    apache = new oval.schemas.definitions.apache.ObjectFactory();
	    catos = new oval.schemas.definitions.catos.ObjectFactory();
	    core = new oval.schemas.definitions.core.ObjectFactory();
	    esx = new oval.schemas.definitions.esx.ObjectFactory();
	    freebsd = new oval.schemas.definitions.freebsd.ObjectFactory();
	    hpux = new oval.schemas.definitions.hpux.ObjectFactory();
	    independent = new oval.schemas.definitions.independent.ObjectFactory();
	    ios = new oval.schemas.definitions.ios.ObjectFactory();
	    linux = new oval.schemas.definitions.linux.ObjectFactory();
	    macos = new oval.schemas.definitions.macos.ObjectFactory();
	    pixos = new oval.schemas.definitions.pixos.ObjectFactory();
	    sharepoint = new oval.schemas.definitions.sharepoint.ObjectFactory();
	    solaris = new oval.schemas.definitions.solaris.ObjectFactory();
	    unix = new oval.schemas.definitions.unix.ObjectFactory();
	    windows = new oval.schemas.definitions.windows.ObjectFactory();
	}
    }

    /**
     * A data structure containing fields for all the OVAL system characteristics object factories.
     */
    public static class SystemCharacteristicsFactories {
	public oval.schemas.systemcharacteristics.aix.ObjectFactory aix;
	public oval.schemas.systemcharacteristics.apache.ObjectFactory apache;
	public oval.schemas.systemcharacteristics.catos.ObjectFactory catos;
	public oval.schemas.systemcharacteristics.core.ObjectFactory core;
	public oval.schemas.systemcharacteristics.esx.ObjectFactory esx;
	public oval.schemas.systemcharacteristics.freebsd.ObjectFactory freebsd;
	public oval.schemas.systemcharacteristics.hpux.ObjectFactory hpux;
	public oval.schemas.systemcharacteristics.independent.ObjectFactory independent;
	public oval.schemas.systemcharacteristics.ios.ObjectFactory ios;
	public oval.schemas.systemcharacteristics.linux.ObjectFactory linux;
	public oval.schemas.systemcharacteristics.macos.ObjectFactory macos;
	public oval.schemas.systemcharacteristics.pixos.ObjectFactory pixos;
	public oval.schemas.systemcharacteristics.sharepoint.ObjectFactory sharepoint;
	public oval.schemas.systemcharacteristics.solaris.ObjectFactory solaris;
	public oval.schemas.systemcharacteristics.unix.ObjectFactory unix;
	public oval.schemas.systemcharacteristics.windows.ObjectFactory windows;

	private SystemCharacteristicsFactories() {
	    aix = new oval.schemas.systemcharacteristics.aix.ObjectFactory();
	    apache = new oval.schemas.systemcharacteristics.apache.ObjectFactory();
	    catos = new oval.schemas.systemcharacteristics.catos.ObjectFactory();
	    core = new oval.schemas.systemcharacteristics.core.ObjectFactory();
	    esx = new oval.schemas.systemcharacteristics.esx.ObjectFactory();
	    freebsd = new oval.schemas.systemcharacteristics.freebsd.ObjectFactory();
	    hpux = new oval.schemas.systemcharacteristics.hpux.ObjectFactory();
	    independent = new oval.schemas.systemcharacteristics.independent.ObjectFactory();
	    ios = new oval.schemas.systemcharacteristics.ios.ObjectFactory();
	    linux = new oval.schemas.systemcharacteristics.linux.ObjectFactory();
	    macos = new oval.schemas.systemcharacteristics.macos.ObjectFactory();
	    pixos = new oval.schemas.systemcharacteristics.pixos.ObjectFactory();
	    sharepoint = new oval.schemas.systemcharacteristics.sharepoint.ObjectFactory();
	    solaris = new oval.schemas.systemcharacteristics.solaris.ObjectFactory();
	    unix = new oval.schemas.systemcharacteristics.unix.ObjectFactory();
	    windows = new oval.schemas.systemcharacteristics.windows.ObjectFactory();
	}
    }

    // Private

    /**
     * Recursively configure the class.
     */
    private static void configureInterface(Class clazz, IProperty prop, List<Class> visited, String sessionClassname) {
	//
	// First, configure all properties from this interface
	//
	try {
	    visited.add(clazz);
	    String section = clazz.getName();
	    for (String key : config.getSection(section)) {
		//
		// Since configuration happens from the bottom-up, make sure not to override any
		// properties that have already been set.
		//
		if (prop.getProperty(key) == null) {
		    String value = config.getProperty(section, key);
		    sysLogger.debug(JOVALMsg.STATUS_CONFIG_SESSION, sessionClassname, key, value, clazz.getName());
		    prop.setProperty(key, config.getProperty(section, key));
		}
	    }
	} catch (NoSuchElementException e) {
	}

	//
	// Then, configure all super-interfaces
	//
	for (Class intf : clazz.getInterfaces()) {
	    if (!visited.contains(intf)) {
		configureInterface(intf, prop, visited, sessionClassname);
	    }
	}

    }
}

// Copyright (C) 2011 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.os.unix.io.driver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.slf4j.cal10n.LocLogger;

import org.joval.intf.io.IFile;
import org.joval.intf.io.IFilesystem;
import org.joval.intf.io.IReader;
import org.joval.intf.unix.io.IUnixFileInfo;
import org.joval.intf.unix.io.IUnixFilesystem;
import org.joval.intf.unix.io.IUnixFilesystemDriver;
import org.joval.intf.unix.system.IUnixSession;
import org.joval.intf.util.ISearchable;
import org.joval.io.AbstractFilesystem.FileInfo;
import org.joval.io.PerishableReader;
import org.joval.os.unix.io.UnixFileInfo;
import org.joval.util.JOVALMsg;
import org.joval.util.SafeCLI;
import org.joval.util.StringTools;

/**
 * IUnixFilesystemDriver implementation for Linux.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public class LinuxDriver extends AbstractDriver {
    public LinuxDriver(IUnixSession session) {
	super(session);
    }

    // Implement IUnixFilesystemDriver

    public Collection<IFilesystem.IMount> getMounts(Pattern typeFilter) throws Exception {
	Collection<IFilesystem.IMount> mounts = new ArrayList<IFilesystem.IMount>();
	for (String line : SafeCLI.multiLine("mount", session, IUnixSession.Timeout.S)) {
	    if (line.length() > 0) {
		StringTokenizer tok = new StringTokenizer(line);
		String device = tok.nextToken();
		tok.nextToken(); // on
		String mountPoint = tok.nextToken();
		tok.nextToken(); // type
		String fsType = tok.nextToken();
		if (typeFilter != null && typeFilter.matcher(fsType).find()) {
		    logger.info(JOVALMsg.STATUS_FS_MOUNT_SKIP, mountPoint, fsType);
		} else if (mountPoint.startsWith(IUnixFilesystem.DELIM_STR)) {
		    logger.info(JOVALMsg.STATUS_FS_MOUNT_ADD, mountPoint, fsType);
		    mounts.add(new Mount(mountPoint, fsType));
		}
	    }
	}
	return mounts;
    }

    public String getFindCommand(List<ISearchable.ICondition> conditions) {
	String from = null;
	boolean dirOnly = false;
	boolean followLinks = false;
	boolean xdev = false;
	Pattern path = null, dirname = null, basename = null;
	String literalBasename = null;
	int depth = ISearchable.DEPTH_UNLIMITED;

	for (ISearchable.ICondition condition : conditions) {
	    switch(condition.getField()) {
	      case IUnixFilesystem.FIELD_FOLLOW_LINKS:
		followLinks = true;
		break;
	      case IUnixFilesystem.FIELD_XDEV:
		xdev = true;
		break;
	      case IFilesystem.FIELD_FILETYPE:
		if (IFilesystem.FILETYPE_DIR.equals(condition.getValue())) {
		    dirOnly = true;
		}
		break;
	      case IFilesystem.FIELD_PATH:
		path = (Pattern)condition.getValue();
		break;
	      case IFilesystem.FIELD_DIRNAME:
		dirname = (Pattern)condition.getValue();
		break;
	      case IFilesystem.FIELD_BASENAME:
		switch(condition.getType()) {
		  case ISearchable.TYPE_EQUALITY:
		    literalBasename = (String)condition.getValue();
		    break;
		  case ISearchable.TYPE_PATTERN:
		    basename = (Pattern)condition.getValue();
		    break;
		}
		break;
	      case ISearchable.FIELD_DEPTH:
		depth = ((Integer)condition.getValue()).intValue();
		break;
	      case ISearchable.FIELD_FROM:
		from = (String)condition.getValue();
		break;
	    }
	}

	StringBuffer cmd = new StringBuffer("find");
	if (followLinks) {
	    cmd.append(" -L");
	}
	cmd.append(" ").append(from);
	if (xdev) {
	    cmd.append(" -mount");
	}
	if (depth != ISearchable.DEPTH_UNLIMITED) {
	    cmd.append(" -maxdepth ").append(Integer.toString(depth));
	}
	if (dirOnly) {
	    cmd.append(" -type d");
	    if (dirname != null) {
		cmd.append(" -regex '").append(dirname.pattern()).append("'");
	    }
	} else {
	    if (path != null) {
		cmd.append(" | grep -E '").append(path.pattern()).append("'");
	    } else {
		if (dirname != null) {
		    cmd.append(" -type d");
		    cmd.append(" | grep -E '").append(dirname.pattern()).append("'");
		    cmd.append(" | xargs -I{} find '{}' -maxdepth 1");
		}
		if (basename != null) {
		    cmd.append(" -type f");
		    cmd.append(" | awk -F/ '$NF ~ /");
		    cmd.append(basename.pattern());
		    cmd.append("/'");
		} else if (literalBasename != null) {
		    cmd.append(" -type f");
		    cmd.append(" -name '").append(literalBasename).append("'");
		}
	    }
	}
	cmd.append(" | xargs -I{} ").append(getStatCommand()).append(" '{}'");
	return cmd.toString();
    }

    public String getStatCommand() {
	return "ls -dnZ --full-time";
    }

    public UnixFileInfo nextFileInfo(Iterator<String> lines) {
	String line = null;
	while(lines.hasNext()) {
	    if ((line = lines.next()).length() > 11) {
		break;
	    }
	}
	if (line == null || line.length() == 0) {
	    return null;
	}

	char unixType = line.charAt(0);
	String permissions = line.substring(1, 10);
	boolean hasExtendedAcl = false;
	if (line.charAt(10) == '+') {
	    hasExtendedAcl = true;
	}

	StringTokenizer tok = new StringTokenizer(line.substring(11));
	String linkCount = tok.nextToken();
	String selinux = tok.nextToken();
	int uid = -1;
	try {
	    uid = Integer.parseInt(tok.nextToken());
	} catch (NumberFormatException e) {
	    //DAS -- could be, e.g., 4294967294 (illegal "nobody" value)
	}
	int gid = -1;
	try {
	    gid = Integer.parseInt(tok.nextToken());
	} catch (NumberFormatException e) {
	    //DAS -- could be, e.g., 4294967294 (illegal "nobody" value)
	}

	FileInfo.Type type = FileInfo.Type.FILE;
	switch(unixType) {
	  case IUnixFileInfo.DIR_TYPE:
	    type = FileInfo.Type.DIRECTORY;
	    break;

	  case IUnixFileInfo.LINK_TYPE:
	    type = FileInfo.Type.LINK;
	    break;

	  case IUnixFileInfo.CHAR_TYPE:
	  case IUnixFileInfo.BLOCK_TYPE:
	    int ptr = -1;
	    if ((ptr = line.indexOf(",")) > 11) {
		tok = new StringTokenizer(line.substring(ptr+1));
	    }
	    break;
	}

	long length = 0;
	try {
	    length = Long.parseLong(tok.nextToken());
	} catch (NumberFormatException e) {
	}

	long mtime = IFile.UNKNOWN_TIME;
	String dateStr = tok.nextToken("/").trim();
	try {
	    String parsable = new StringBuffer(dateStr.substring(0, 23)).append(dateStr.substring(29)).toString();
	    mtime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").parse(parsable).getTime();
	} catch (ParseException e) {
	    logger.warn(JOVALMsg.getMessage(JOVALMsg.ERROR_EXCEPTION), e);
	}

	String path = null, linkPath = null;
	int begin = line.indexOf(session.getFilesystem().getDelimiter());
	if (begin > 0) {
	    int end = line.indexOf("->");
	    if (end == -1) {
		path = line.substring(begin).trim();
	    } else if (end > begin) {
		path = line.substring(begin, end).trim();
		linkPath = line.substring(end+2).trim();
	    }
	}

	Properties extended = new Properties();
	extended.setProperty(IUnixFileInfo.SELINUX_DATA, selinux);

	return new UnixFileInfo(IFile.UNKNOWN_TIME, mtime, IFile.UNKNOWN_TIME, type, length, path, linkPath,
				unixType, permissions, uid, gid, hasExtendedAcl, extended);
    }
}

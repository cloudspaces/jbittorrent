/*
 * jbittorrent library is an implementation in Java language of BiTorrent protocol.
 *
 * It is based on the Java Bittorrent API of Baptiste Dubuis, Artificial Inteligency Laboratory, EPFL.
 * @version 1.0
 * @author Baptiste Dubuis
 * To contact the author:
 * email: baptiste.dubuis@gmail.com
 *
 * More information about Java Bittorrent API:
 * http://sourceforge.net/projects/bitext/
 *
 * New contribution are:
 * 1. Optimization of process establishement of conecctions betwen the peers of the swarm.
 * 2. Improvements in the Choking Algorithm.
 * 3. Improvements in the Optimistic Unchoking implementation.
 * 4. Implementation of Rarest First algorithm .
 * 5. Implementation of End Game Strategy.
 *
 * This project contains three packs:
 * 1. jbittorrent is the "client" part, i.e. it implements all classes needed to publish files, share them and download them.
 * 2. trackerBT is the "tracker" part, i.e. it implements all classes needed to run a Bittorrent tracker that coordinates peers exchanges.
 * 3. test contains example classes on how a developer could create new applications and new .torrent file.
 *
 * Copyright (C) 2013 Sandra Ferrer, AST Research Group
 *
 * jbittorrent is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published 
 * by the Free Software Foundation; either version 2 of the License, 
 * or (at your option) any later version.
 *
 * jbittorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  @version 1.0
 *  @author Sandra Ferrer Celma <sandra.ferrer@urv.cat>
 * 
 */

package jbittorrent;

import java.nio.charset.Charset;

/**
 * Some useful (or not...) constants used (or not yet...) throughout the program
 */
public class
        Constants {
    public static final String DEFAULT_ENCODING = "UTF8";
    public static final String BYTE_ENCODING = "ISO-8859-1";
    public static Charset BYTE_CHARSET;
    public static Charset DEFAULT_CHARSET;

    static {
        try {

            BYTE_CHARSET = Charset.forName(Constants.BYTE_ENCODING);
            DEFAULT_CHARSET = Charset.forName(Constants.DEFAULT_ENCODING);

        } catch (Throwable e) {

            e.printStackTrace();
        }
    }

    public static final String CLIENT = "jbittorrent 1.0";
    //public static String SAVEPATH = "downloads/";

    public static final String OSName = System.getProperty("os.name");

    public static final boolean isOSX = OSName.toLowerCase().startsWith(
            "mac os");
    public static final boolean isLinux = OSName.equalsIgnoreCase("Linux");
    public static final boolean isSolaris = OSName.equalsIgnoreCase("SunOS");
    public static final boolean isFreeBSD = OSName.equalsIgnoreCase("FreeBSD");
    public static final boolean isWindowsXP = OSName.equalsIgnoreCase(
            "Windows XP");
    public static final boolean isWindows95 = OSName.equalsIgnoreCase(
            "Windows 95");
    public static final boolean isWindows98 = OSName.equalsIgnoreCase(
            "Windows 98");
    public static final boolean isWindowsME = OSName.equalsIgnoreCase(
            "Windows ME");
    public static final boolean isWindows9598ME = isWindows95 || isWindows98 ||
                                                  isWindowsME;
    public static final boolean isWindows = OSName.toLowerCase().startsWith(
            "windows");
    public static final String JAVA_VERSION = System.getProperty("java.version");
}

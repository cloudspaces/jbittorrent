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

package test;

import jbittorrent.DownloadManager;
import jbittorrent.TorrentFile;
import jbittorrent.TorrentProcessor;
import jbittorrent.Utils;

/**
 * Simple example to show how it is possible to download files using bittorrent
 * protocol with a given .torrent file
 * 
 * @author Baptiste Dubuis
 * @version 0.1
 * 
 */
public class ExampleDownloadFiles {

	/**
	 * .../jbittorrent/example/client2/funvideo.torrent .../jbittorrent/example/client2/
	 */
	public static void main(String[] args) {
        try {
            TorrentProcessor tp = new TorrentProcessor();

            if(args.length < 1){
                System.err.println("Incorrect use, please provide the path of the torrent file...\r\n" +
                        		   "\r\nCorrect use of ExampleDownloadFiles:\r\n"+
                        		   "ExampleDownloadFiles torrentPath, savePath");
                System.exit(1);
            }
            TorrentFile t = tp.getTorrentFile(tp.parseTorrent(args[0]));
            //if(args.length > 1)
                //Constants.SAVEPATH = args[1];
            if (t != null) {
            	
            	DownloadManager dm = null;
            	dm = new DownloadManager(t, Utils.generateID(), args[1]);
                dm.startListening(6881, 6889);
                dm.startTrackerUpdate();
                dm.start();
                /*dm.blockUntilCompletion();
                dm.stopTrackerUpdate();
                dm.closeTempFiles();*/
            } else {
                System.err.println("Provided file is not a valid torrent file");
                System.err.flush();
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("Error while processing torrent file. Please restart the client");
            e.printStackTrace();
            System.exit(1);
        }
    }    
}

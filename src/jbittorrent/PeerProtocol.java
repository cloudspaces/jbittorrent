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


/**
 * Constants used in Peer Protocol.
 *
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class PeerProtocol {
	
    public static final int HANDSHAKE = -1;
    public static final int KEEP_ALIVE = 0;
    public static final int CHOKE = 1;
    public static final int UNCHOKE = 2;
    public static final int INTERESTED = 3;
    public static final int NOT_INTERESTED = 4;
    public static final int HAVE = 5;
    public static final int BITFIELD = 6;
    public static final int REQUEST = 7;
    public static final int PIECE = 8;
    public static final int CANCEL = 9;
    public static final int PORT = 10;
    
    /*
     * Are a type of internal messages that doesn’t belong to the bittorrent protocol.  
     * They are used in the End Game mode.  Advise of the currently selected piece has 
     * already been downloaded by another task.  It is necessary to send the messages 
     * of cancelation of blocks and to restart the process of selection of pieces.
     */
    public static final int REFRESH_ENDGAME_STRATEGY = 11;
    public static final int CAMCEL_PIECE_ENDGAME_STRATEGY = 12;
    
    public static final String[] TYPE = {"Keep_Alive", "Choke", "Unchoke",
                                        "Interested", "Not_Interested", "Have",
                                        "Bitfield", "Request", "Piece",
                                        "Cancel", "Port", "Refresh_EndGame_Strategy", "Cancel_Piece_EndGame_Strategy"};

    public static int BLOCK_SIZE =16384;
    public static final byte[] BLOCK_SIZE_BYTES = Utils.intToByteArray(16384);

}

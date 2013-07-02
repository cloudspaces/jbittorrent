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

import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashMap;

/**
 * Compares the popularity of two pieces.  One piece is more popular than another, 
 * if the number of peers of swarm that have this piece is greater.
 * 
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class PopularFirstComparator implements Comparator<Integer> {

	private LinkedHashMap<String, BitSet> peerAvailabilies;
	
	public PopularFirstComparator(LinkedHashMap<String, BitSet> peerAvailabilies){
		
		this.peerAvailabilies = peerAvailabilies;
	}
	
	/**
	 * Gets the number of peers that have the piece with this index.
	 * @param indexPiece int Index of the piece
	 * @return int
	 */
	public int getNumFriendPeers(int indexPiece){
		
		int n_friendPeers = 0;
		
		for(BitSet b:this.peerAvailabilies.values()){
			if(b.get(indexPiece)){
				n_friendPeers++;
			}
		}
		
		return n_friendPeers;
	}
	
	/**
     * Compares its two arguments for order.
     *
     * @param indexPieceA the first object to be compared.
     * @param indexPieceB the second object to be compared.
     * @return a positive integer, zero, or a negative integer as the first 
     * corresponds to a piece that is in a larger number of peers, 
     * equal to, or greater than the second.
     */
	public int compare(Integer indexPieceA, Integer indexPieceB) {
		
		if (this.getNumFriendPeers(indexPieceA) < this.getNumFriendPeers(indexPieceB)){
            return -1;
        }else if (this.getNumFriendPeers(indexPieceA) > this.getNumFriendPeers(indexPieceB)){
            return 1;
        }
		
		return 0;
	}
}

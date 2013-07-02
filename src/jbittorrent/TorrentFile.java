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

import java.util.ArrayList;
import java.util.Date;

/**
 * Representation of a torrent file
 *
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 */
public class TorrentFile {

    public String announceURL;
    public String scrapeURL;
    public String comment;
    public String createdBy;
    public long creationDate;
    public String encoding;
    public String saveAs;
    
    public int pieceLength;
    
    public String piecesPath;
    public String container;

    /* In case of multiple files torrent, saveAs is the name of a directory
     * and name contains the path of the file to be saved in this directory
     */
    public ArrayList<String> name;
    public ArrayList<Long> length;

    public byte[] info_hash_as_binary;
    public String info_hash_as_hex;
    public String info_hash_as_url;
    public long total_length;

    public ArrayList<byte[]> piece_hash_values_as_binary;
    public ArrayList<String> piece_hash_values_as_hex;
    public ArrayList<String> piece_hash_values_as_url;

    /**
     * Create the TorrentFile object and initiate its instance variables
     */
    public TorrentFile() {
        super();
        announceURL = new String();
        scrapeURL = null;
        comment = new String();
        createdBy = new String();
        encoding = new String();
        saveAs = new String();
        creationDate = -1;
        total_length = -1;
        pieceLength = -1;

        piecesPath = null;
        container = null;
        
        name = new ArrayList<String>();
        length = new ArrayList<Long>();

        piece_hash_values_as_binary = new ArrayList<byte[]>();
        piece_hash_values_as_url = new ArrayList<String>();
        piece_hash_values_as_hex = new ArrayList<String>();
        info_hash_as_binary = new byte[20];
        info_hash_as_url = new String();
        info_hash_as_hex = new String();
    }

    /**
     * Print the torrent information in a readable manner.
     * @param detailed Choose if we want a detailed output or not. Detailed
     * output prints the comment, the files list and the pieces hashes while the
     * standard output simply prints tracker url, creator, creation date and
     * info hash
     */
    public void printData(boolean detailed) {
        System.out.println("Tracker AnnounceURL: " + this.announceURL);
        if(this.scrapeURL!=null)
            System.out.println("Tracker ScrapeURL: " + this.scrapeURL);
        else
        	System.out.println("Tracker ScrapeURL Not Found");
        	
        System.out.println("Torrent created by : " + this.createdBy);
        System.out.println("Torrent creation date : " + new Date(this.creationDate));
        
        if(this.piecesPath!=null)
        	System.out.println("Path pieces: "+this.piecesPath);
        if(this.container!=null)
        	System.out.println("Cointainer pieces:"+this.container);
        
        System.out.println("Info hash :\n");
        System.out.println("\t\t" + new String(this.info_hash_as_binary));
        System.out.println("\t\t" + this.info_hash_as_hex);
        System.out.println("\t\t" + this.info_hash_as_url);
        if(detailed){
            System.out.println("Comment :" + this.comment);
            System.out.println("\nFiles List :\n");
            for (int i = 0; i < this.length.size(); i++)
                System.out.println("\t- " + this.name.get(i) + " ( " +
                                   this.length.get(i) + " Bytes )");
            System.out.println("\n");
            System.out.println("Pieces hashes (piece length = " +
                               this.pieceLength + ") :\n");
            for (int i = 0; i < this.piece_hash_values_as_binary.size(); i++) {
               // System.out.println((i + 1) + ":\t\t" +new String(this.piece_hash_values_as_binary.get(i)));
                System.out.println((i + 1) + ":\t\t" + this.piece_hash_values_as_hex.get(i));
                //System.out.println("\t\t" + this.piece_hash_values_as_url.get(i));

            }
        }

    }

}

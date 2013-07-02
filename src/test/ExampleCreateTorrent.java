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

import jbittorrent.TorrentProcessor;

import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * Simple example to show how it is possible to create a new .torrent file to
 * share files using bittorrent protocol
 * 
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class ExampleCreateTorrent{
	
	/**
	 * dir file: .../jbittorrent/example/client1/funvideo05.wmv
	 * dir file torrent: .../jbittorrent/example/client1/funvideo.torrent
	 * server Tracker Announce: http://.../announce.php
	 * creator:John Lynch
	 * coment: this is a fun video
	 * if used peerTracker 
	 * server Tracker Scrape: http://www.peertracker.com/scrape.php
	 * 
	 * .../jbittorrent/example/client1/funvideo.torrent http://.../announce.php 256 .../jbittorrent/example/client1/funvideo05.wmv .. "John Lynch" .. "this is a fun video" .. http://www.peertrackerlocal.com/scrape.php
	 * 
	 * @param args
	 */
    public static void main(String[] args){
    	
        if(args.length < 5){
            System.err.println("Wrong parameter number\r\n\r\nUse:\r\n" +
                               "ExampleCreateTorrent <torrentPath> <announce url> <pieceLength> " +
                               "<filePath1> <filePath2> ... <..> <creator> <..> <comment> <..> <scrape url>");
            System.exit(0);
        }
        TorrentProcessor tp = new TorrentProcessor();
        tp.setAnnounceURL(args[1]);
        try{
            tp.setPieceLength(Integer.parseInt(args[2]));
        }catch(Exception e){
            System.err.println("Piece length must be an integer");
            System.exit(0);
        }
        int i = 3;
        ArrayList<String> files = new ArrayList<String>();
        if(!args[i+1].equalsIgnoreCase("..")){
            tp.setName(args[3]);
            i++;
        }
        while(i < args.length){
            if(args[i].equalsIgnoreCase(".."))
                break;
            files.add(args[i]);
            i++;
        }
        try{
            tp.addFiles(files);
        }catch(Exception e){
            System.err.println("Problem when adding files to torrent. Check your data");
            System.exit(0);
        }
        i++;
        String creator = "";
        while(i < args.length){
            if(args[i].equalsIgnoreCase(".."))
                break;
            creator += args[i];
            i++;
        }
        tp.setCreator(creator);
        i++;
        String comment = "";
        while(i < args.length){
            if(args[i].equalsIgnoreCase(".."))
                break;
            comment += args[i];
            i++;
        }
        tp.setComment(comment);
        
        i++;
        String scrapeUrl = "";
        while(i < args.length){
            if(args[i].equalsIgnoreCase(".."))
                break;
            scrapeUrl += args[i];
            i++;
        }
        tp.setScrapeURL(scrapeUrl);
        
        try{
            System.out.println("Hashing the files...");
            System.out.flush();
            tp.generatePieceHashes();
            System.out.println("Hash complete... Saving...");
            FileOutputStream fos = new FileOutputStream(args[0]);
            fos.write(tp.generateTorrent());
            System.out.println("Torrent created successfully!!!");
            fos.close();
            
            tp.getTorrent().printData(true);
        }catch(Exception e){
            System.err.println("Error when writing to the torrent file...");
            System.exit(1);
        }
    }
}

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 *
 * Class enabling to process a torrent file
 * 
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class TorrentProcessor {

    private TorrentFile torrent;


    public TorrentProcessor(TorrentFile torrent){
        this.torrent = torrent;
    }

    public TorrentProcessor(){
        this.torrent = new TorrentFile();
    }

    /**
     * Given the path of a torrent, parse the file and represent it as a Map
     * @param filename String
     * @return Map
     */
    public Map<String, Object> parseTorrent(String filename){
        return this.parseTorrent(new File(filename));
    }

    /**
     * Given a File (supposed to be a torrent), parse it and represent it as a Map
     * @param file File
     * @return Map
     */
    public Map<String, Object> parseTorrent(File file){
        try{
            return BDecoder.decode(IOManager.readBytesFromFile(file));
        } catch(IOException ioe){}
        return null;
    }
    
    /**
     * Given the path of a torrent, parse the byte array of file and represent it as a Map
     * @param filedate byte[]
     * @return Map
     */
    public Map<String, Object> parseTorrent(byte[] filedate){
    	try{
            return BDecoder.decode(filedate);
        } catch(IOException ioe){}
        return null;
    }

    /**
     * Given a Map, retrieve all useful information and represent it as a TorrentFile object
     * @param m Map
     * @return TorrentFile
     */
    @SuppressWarnings("unchecked")
	public TorrentFile getTorrentFile(Map<String,Object> m){
        if(m == null)
            return null;
        if(m.containsKey("announce")) // mandatory key
            this.torrent.announceURL = new String((byte[]) m.get("announce"));
        else
            return null;
        if(m.containsKey("scrape"))
        	this.torrent.scrapeURL = new String((byte[]) m.get("scrape"));
        if(m.containsKey("comment")) // optional key
            this.torrent.comment = new String((byte[]) m.get("comment"));
        if(m.containsKey("created by")) // optional key
            this.torrent.createdBy = new String((byte[]) m.get("created by"));
        if(m.containsKey("creation date")) // optional key
            this.torrent.creationDate = (Long) m.get("creation date");
        if(m.containsKey("encoding")) // optional key
            this.torrent.encoding = new String((byte[]) m.get("encoding"));

        //Store the info field data
        if(m.containsKey("info")){
            Map<String, Object> info = (Map<String, Object>) m.get("info");
            try{

                this.torrent.info_hash_as_binary = Utils.hash(BEncoder.encode(info));
                this.torrent.info_hash_as_hex = Utils.byteArrayToByteString(
                                                this.torrent.info_hash_as_binary);
                this.torrent.info_hash_as_url = Utils.byteArrayToURLString(
                                                this.torrent.info_hash_as_binary);
            }catch(IOException ioe){return null;}
            
            if (info.containsKey("name"))
            	this.torrent.saveAs = new String((byte[]) info.get("name"));
            
            if (info.containsKey("piece length")){
                this.torrent.pieceLength = ((Long) info.get("piece length")).intValue();
            }else
                return null;
            
            if(info.containsKey("piecesPath"))
            	this.torrent.piecesPath = new String((byte[]) info.get("piecesPath"));
            if(info.containsKey("container"))
            	this.torrent.container = new String((byte[]) info.get("container"));
            

            if (info.containsKey("pieces")) {
                byte[] piecesHash2 = (byte[]) info.get("pieces");
                if (piecesHash2.length % 20 != 0)
                    return null;

                for (int i = 0; i < piecesHash2.length / 20; i++) {
                    byte[] temp = Utils.subArray(piecesHash2, i * 20, 20);
                    this.torrent.piece_hash_values_as_binary.add(temp);
                    this.torrent.piece_hash_values_as_hex.add(Utils.
                            byteArrayToByteString(temp));
                    this.torrent.piece_hash_values_as_url.add(Utils.
                            byteArrayToURLString(temp));
                }
            } else
                return null;

            if (info.containsKey("files")) {
                List<Object> multFiles = (List<Object>) info.get("files");
                this.torrent.total_length = 0;
                for (int i = 0; i < multFiles.size(); i++) {
                    this.torrent.length.add((long) ((Long) ((Map<Object, Object>) multFiles.get(i)).
                                             get("length")).intValue());
                    this.torrent.total_length += ((Long) ((Map<Object, Object>) multFiles.get(i)).
                                                  get("length")).intValue();

                    List<Object> path = (List<Object>) ((Map<Object, Object>) multFiles.get(i)).get("path");
                    String filePath = "";
                    for (int j = 0; j < path.size(); j++) {
                        filePath += new String((byte[]) path.get(j));
                    }
                    this.torrent.name.add(filePath);
                }
            } else {
                this.torrent.length.add((long) ((Long) info.get("length")).intValue());
                this.torrent.total_length = ((Long) info.get("length")).intValue();
                this.torrent.name.add(new String((byte[]) info.get("name")));
            }
        }else
            return null;
        return this.torrent;
    }

    /**
     * Sets the TorrentFile object of the Publisher equals to the given one
     * @param torr TorrentFile
     */
    public void setTorrent(TorrentFile torr) {
        this.torrent = torr;
    }

    /**
     * Updates the TorrentFile object according to the given parameters
     * @param url The announce url
     * @param pLength The length of the pieces of the torrent
     * @param comment The comments for the torrent
     * @param encoding The encoding of the torrent
     * @param filename The path of the file to be added to the torrent
     */
    public void setTorrentData(String url, int pLength, String comment,
                               String encoding, String filename) {
        this.torrent.announceURL = url;
        this.torrent.pieceLength = pLength * 1024;
        this.torrent.createdBy = Constants.CLIENT;
        this.torrent.comment = comment;
        this.torrent.creationDate = System.currentTimeMillis();
        this.torrent.encoding = encoding;
        this.addFile(filename);
    }
    
    /**
     * Updates the TorrentFile object according to the given parameters
     * @param announceURL The announce url
     * @param scrapeURL the scrape url
     * @param pLength The length of the pieces of the torrent
     * @param comment The comments for the torrent
     * @param encoding The encoding of the torrent
     * @param filename The path of the file to be added to the torrent
     */
    public void setTorrentData(String announceURL, String scrapeURL, int pLength, String comment,
                               String encoding, String filename) {
        this.torrent.announceURL = announceURL;
        this.torrent.scrapeURL = scrapeURL;
        this.torrent.pieceLength = pLength * 1024;
        this.torrent.createdBy = Constants.CLIENT;
        this.torrent.comment = comment;
        this.torrent.creationDate = System.currentTimeMillis();
        this.torrent.encoding = encoding;
        this.addFile(filename);
    }

    /**
     * Updates the TorrentFile object according to the given parameters
     * @param url The announce url
     * @param pLength The length of the pieces of the torrent
     * @param comment The comments for the torrent
     * @param encoding The encoding of the torrent
     * @param name The name of the directory to save the files in
     * @param filenames The path of the file to be added to the torrent
     * @throws java.lang.Exception
     */
    public void setTorrentData(String url, int pLength, String comment,
                               String encoding, String name, List<String> filenames) throws Exception {
        this.torrent.announceURL = url;
        this.torrent.pieceLength = pLength * 1024;
        this.torrent.comment = comment;
        this.torrent.createdBy = Constants.CLIENT;
        this.torrent.creationDate = System.currentTimeMillis();
        this.torrent.encoding = encoding;
        this.torrent.saveAs = name;
        this.addFiles(filenames);
    }

    /**
     * Sets the announce url of the torrent
     * @param url String
     */
    public void setAnnounceURL(String url) {
        this.torrent.announceURL = url;
    }

    /**
     * Sets the scrape url of the torrent
     * @param url String
     */
    public void setScrapeURL(String url) {
        this.torrent.scrapeURL = url;
    }
    
    /**
     * Sets the pieceLength
     * @param length int
     */
    public void setPieceLength(int length) {
        this.torrent.pieceLength = length * 1024;
    }
    
    /**
     * Sets the total length of file
     * @param totalLength int
     */
    public void setTotalLength(int totalLength){
    	this.torrent.total_length = totalLength;
    }
    
    /**
     * Sets the path of cunkpieces
     * @param piecesPath String
     */
    public void setPiecesPath(String piecesPath){
    	this.torrent.piecesPath = piecesPath;
    }
    
    /**
     * Sets the container of cunkpieces
     * @param piecesContainer String
     */
    public void setPiecesContainer(String piecesContainer){
    	this.torrent.container = piecesContainer;
    }

    /**
     * Sets the directory the files have to be saved in (in case of multiple files torrent)
     * @param name String
     */
    public void setName(String name) {
        this.torrent.saveAs = name;
    }

    /**
     * Sets the comment about this torrent
     * @param comment String
     */
    public void setComment(String comment) {
        this.torrent.comment = comment;
    }

    /**
     * Sets the creator of the torrent. This should be the client name and version
     * @param creator String
     */
    public void setCreator(String creator) {
        this.torrent.createdBy = creator;
    }

    /**
     * Sets the time the torrent was created
     * @param date long
     */
    public void setCreationDate(long date) {
        this.torrent.creationDate = date;
    }

    /**
     * Sets the encoding of the torrent
     * @param encoding String
     */
    public void setEncoding(String encoding) {
        this.torrent.encoding = encoding;
    }

    /**
     * Add the files in the list to the torrent
     * @param l A list containing the File or String object representing the files to be added
     * @return int The number of files that have been added
     * @throws Exception
     */
    public int addFiles(List<String> l) throws Exception {
        return this.addFiles(l.toArray());
    }

    /**
     * Add the files in the list to the torrent
     * @param file The file to be added
     * @return int The number of file that have been added
     * @throws Exception
     */
    public int addFile(File file) {
        return this.addFiles(new File[] {file});
    }

    /**
     * Add the files in the list to the torrent
     * @param filename The path of the file to be added
     * @return int The number of file that have been added
     * @throws Exception
     */
    public int addFile(String filename) {
        return this.addFiles(new String[] {filename});
    }
    
    
    public void addFileName(String filename, long totalLength){
    	 this.torrent.total_length = totalLength;
         this.torrent.name.add(filename);
         this.torrent.length.add(totalLength);
    }
    
    
    /**
     * Add the files in the list to the torrent
     * @param filenames An array containing the files to be added
     * @return int The number of files that have been added
     * @throws Exception
     */
    public int addFiles(Object[] filenames) {
        int nbFileAdded = 0;
        if (this.torrent.total_length == -1)
            this.torrent.total_length = 0;

        for (int i = 0; i < filenames.length; i++) {
            File f = null;
            if (filenames[i] instanceof String)
                f = new File((String) filenames[i]);
            else if (filenames[i] instanceof File)
                f = (File) filenames[i];
            if (f != null)
                if (f.exists()) {
                    this.torrent.total_length += f.length();
                    this.torrent.name.add(f.getPath());
                    this.torrent.length.add((long) new Long(f.length()).intValue());
                    nbFileAdded++;
                }
        }
        return nbFileAdded;
    }
    

    /**
     * Generate the SHA-1 hashes for the file in the torrent in parameter
     * @param torr TorrentFile
     */
    public void generatePieceHashes(TorrentFile torr) {
        ByteBuffer bb = ByteBuffer.allocate(torr.pieceLength);

        torr.piece_hash_values_as_binary.clear();
        for (int i = 0; i < torr.name.size(); i++) {
            File f = new File((String) torr.name.get(i));
            if (f.exists()) {
                try {
                    @SuppressWarnings("resource")
					FileInputStream fis = new FileInputStream(f);
                    int read = 0;
                    byte[] data = new byte[torr.pieceLength];
                    byte[] hash;
                    while ((read = fis.read(data, 0, bb.remaining())) != -1) {
                        bb.put(data, 0, read);
                        if (bb.remaining() == 0) {
                        	hash = Utils.hash(bb.array());
                            torr.piece_hash_values_as_binary.add(hash);
                            torr.piece_hash_values_as_hex.add(Utils.
                                    byteArrayToByteString(hash));
                            torr.piece_hash_values_as_url.add(Utils.
                                    byteArrayToURLString(hash));
                            bb.clear();
                        }
                    }
                } catch (FileNotFoundException fnfe) {} catch (IOException ioe) {}
            }
        }
        if (bb.remaining() != bb.capacity()){
        	byte[] hash = Utils.hash(Utils.subArray(bb.array(), 0, bb.capacity() - bb.remaining()));
            torr.piece_hash_values_as_binary.add(hash);
            torr.piece_hash_values_as_hex.add(Utils.
                    byteArrayToByteString(hash));
            torr.piece_hash_values_as_url.add(Utils.
                    byteArrayToURLString(hash));
        }
    }
    /**
     * Generate the SHA-1 hashes for the files in the current object TorrentFile
     */
    public void generatePieceHashes() {
        this.generatePieceHashes(this.torrent);
    }

    
    public String getMD5File(File file) throws NoSuchAlgorithmException, FileNotFoundException{
    	
    	MessageDigest digest = MessageDigest.getInstance("MD5");
		InputStream is = new FileInputStream(file);
		byte[] buffer = new byte[8192];
		int read = 0;
		
		String sMD5 = "";
		
		try {
			while( (read = is.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}		
			byte[] md5sum = digest.digest();
			BigInteger bigInt = new BigInteger(1, md5sum);
			sMD5 = bigInt.toString(16);
			System.out.println("MD5: " + sMD5);
		} catch(IOException e) {
			throw new RuntimeException("Unable to process file for MD5", e);
		} finally {
			try {
				is.close();
			} catch(IOException e) {
				throw new RuntimeException("Unable to close input stream for MD5 calculation", e);
			}
		}
    	
    	return sMD5;
    }
    
    
    
    /**
     * Generate the bytes of the bencoded TorrentFile data
     * @param torr TorrentFile
     * @return byte[]
     */
    public byte[] generateTorrent(TorrentFile torr) {
        SortedMap<String, Object> map = new TreeMap<String, Object>();
        map.put("announce", torr.announceURL);
        if(torr.scrapeURL != null)
        	map.put("scrape", torr.scrapeURL);
        if(torr.comment.length() > 0)
            map.put("comment", torr.comment);
        if(torr.creationDate >= 0)
            map.put("creation date", torr.creationDate);
        if(torr.createdBy.length() > 0)
            map.put("created by", torr.createdBy);

        SortedMap<String, Serializable> info = new TreeMap<String, Serializable>();
        if (torr.name.size() == 1) {
            File file = new File((String) torr.name.get(0));
        	info.put("length", (Integer) torr.length.get(0).intValue());
            info.put("name", file.getName());
            
            if(torr.piecesPath!=null)
           	 info.put("piecesPath", torr.piecesPath);
            if(torr.container!=null)
           	 info.put("container", torr.container);
                       
            try {
				info.put("md5sum", getMD5File(file));
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}            
        } else {
            if (!torr.saveAs.matches("")){
            	info.put("name", torr.saveAs);
            } else{
                info.put("name", "noDirSpec");
            }
            ArrayList<SortedMap<String, Serializable>> files = new ArrayList<SortedMap<String, Serializable>>();
            for (int i = 0; i < torr.name.size(); i++) {
                SortedMap<String, Serializable> file = new TreeMap<String, Serializable>();
                file.put("length", (Integer) torr.length.get(i).intValue());
                //String[] path = ((String) torr.name.get(i)).split("\\");
                String relativePath = torr.name.get(i).toString();//.replace(info.get("name").toString(), "");
                relativePath = relativePath.substring(relativePath.indexOf(info.get("name").toString()));
                relativePath = relativePath.replace(info.get("name").toString() + File.separator, "");
                
                String[] path = relativePath.split(File.separator);
                @SuppressWarnings("unused")
				File f = new File((String)(torr.name.get(i)));

                ArrayList<String> pathList = new ArrayList<String>();
                for (int j = (path.length > 1) ? 1 : 0; j < path.length; j++)
                    pathList.add(path[j]);
                /*if(path[path.length - 1].compareTo("example.html") == 0){
                	pathList.add(path[path.length - 2]);
                }
                
                pathList.add(path[path.length - 1]);
                */
                file.put("path", pathList); 
                
                /*try {
                	file.put("md5sum", getMD5File(f));
    			} catch (NoSuchAlgorithmException e) {
    				e.printStackTrace();
    			} catch (FileNotFoundException e) {
    				e.printStackTrace();
    			}*/
                
                files.add(file);
            }
            info.put("files", files);            
        }
        info.put("piece length", torr.pieceLength);
        byte[] pieces = new byte[0];
        for (int i = 0; i < torr.piece_hash_values_as_binary.size(); i++)
            pieces = Utils.concat(pieces,
                                  (byte[]) torr.piece_hash_values_as_binary.
                                  get(i));
        info.put("pieces", pieces);
        map.put("info", info);
        try {
            byte[] data = BEncoder.encode(map);
            return data;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Generate the bytes for the current object TorrentFile
     * @return byte[]
     */
    public byte[] generateTorrent() {
        return this.generateTorrent(this.torrent);
    }

    /**
     * Returns the local TorrentFile in its current state
     * @return TorrentFile
     */
    public TorrentFile getTorrent(){
        return this.torrent;
    }

}

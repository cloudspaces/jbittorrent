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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;


/**
 * Object that manages all concurrent downloads. It chooses which piece to request
 * to which peer.
 * 
 * @author Baptiste Dubuis
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class DownloadManager extends Thread implements DTListener, PeerUpdateListener, ConListenerInterface{
	
	private int blockSize = PeerProtocol.BLOCK_SIZE;
	
	private DownloadManagerListener downloadManagerListener = null;
	
	private long lastTimeSendPieceBlock = -1;
	private long lastTimeReceivePieceBlock = -1;
	private long initTimeStartedProtocol = -1;
	
	private static Logger logger = Logger.getLogger(DownloadManager.class);
	
	private int intervalUpdateListPeers = 60;
	
	private float thresholdEndGameTest = -1;
	private Piece downloadEndGamePiece = null;
	
	//For logs
	private boolean saveInformationPieceTransfer = false;
	private LinkedHashMap<String, Peer> peerLogsPiecesTransferred = new LinkedHashMap<String, Peer>();
	private int numPieceSent = 0;
	private long task_duration = 0;
	private String initTime = null;
	private String endTime = null;
	
    // Client ID
    private byte[] clientID;

    private TorrentFile torrent = null;

	@SuppressWarnings("unused")
	private int maxConnectionNumber = 100;

    private int nbOfFiles = 0;
    private long length = 0;
    private long left = 0;
    private Piece[] pieceList;
    private BitSet isComplete;
    private BitSet isActiveRequests;
    private BitSet isRequested;
    private BitSet isPieceSent;
    private int nbPieces;
    private RandomAccessFile[] output_files;

    private PeerUpdater pu = null;
    private ConnectionListener cl = null;

    private List<Peer> unchokeList = new LinkedList<Peer>();

    private LinkedHashMap<String, Peer> peerList = new LinkedHashMap<String, Peer>();
    private LinkedHashMap<String, Peer> peerIdTrackerList = new LinkedHashMap<String, Peer>();
    private LinkedHashMap<String, Peer> peerIPListeningPortList = new LinkedHashMap<String, Peer>();
    private TreeMap<String, DownloadTask> task = new TreeMap<String, DownloadTask>();
    private LinkedHashMap<String, BitSet> peerAvailabilies = new LinkedHashMap<String, BitSet>();

    private LinkedHashMap<String, Peer> unchoken = new LinkedHashMap<String,Peer>();
    private long lastUnchoking = 0;
    private short optimisticUnchoke = 3;
    private String savePath;
    private boolean runBlockUntil;
    
    
    /**
     * Creates a new manager in accord to the given torrent, 
     * using the client ID provided and the path where is saved the downloaded file.   
     * @param torrent TorrentFile
     * @param clientID byte[]
     * @param savePath is the path where the downloaded file is saved.
     */
    public DownloadManager(TorrentFile torrent, final byte[] clientID, String savePath) {
    	
    	this.torrent = torrent;
    	this.savePath = savePath;
    	this.clientID = clientID;
        
    	this.blockSize = PeerProtocol.BLOCK_SIZE;
    	this.intervalUpdateListPeers = 170;
    	this.thresholdEndGameTest = -1;
    	
    	this.initialization();
    	
    }
    
    /**
     * Creates a new manager in accord to the given torrent, 
     * using the client ID provided and the path where is saved the downloaded file.  
     * The fourth parameter determines the size (in kb) of blocks, 
     * in which are divided the pieces, default size is 16 kB.
     * 
     * @param torrent TorrentFile
     * @param clientID byet[]
     * @param savePath String
     * @param blockSize int
     */
    public DownloadManager(TorrentFile torrent, final byte[] clientID, String savePath, int blockSize) {

    	this.torrent = torrent;
    	this.blockSize = blockSize * 1024;
    	this.savePath = savePath;
    	this.clientID = clientID;
    	
    	this.intervalUpdateListPeers = 170;
    	this.thresholdEndGameTest = -1;
    	
    	this.initialization();
    }
    
    /**
     * Creates a new manager in accord to the given torrent, 
     * using the client ID provided and the path where is saved the downloaded file.
     * Also according with the following parameters:
     * <ul>
     * <li>intervalUpdateListPeers ­ intervalUpdateListPeers: establishes the periodicity, in seconds, 
     * with which the peer is announced in the tracker; in accordance to the tracker protocol HTTP/HTTPS 
     * and the announceURL specified in the TorrentFile.  The connections of the peers with 
     * which it interacts, will be revised, and new connections will be established, if the peers 
     * list returned by the tracker, contains new peers.</li>
     * <li>blockSize determines the size, in KB, of the blocs.  By default is 16 kB.</li>
     * <li>thresholdEndGameTest ­ thresholdEndGameTest: determines when to activate EndGameStrategy.  
     * If it is ­1 (by default value) will be activated according to the protocol defined by Bram Cohen 
     * (when all the pieces have been solicited). Otherwise, a percentage of total download 
     * of the file (i.e. 90%) is determined like the moment where will be activated the EndGameStrategy.</li>
     * </ul>
     * @param torrent TorrentFile
     * @param clientID byet[]
     * @param savePath String
     * @param intervalUpdateListPeers int
     * @param blockSize int
     * @param thresholdEndGameTest float
     */
    public DownloadManager(TorrentFile torrent, final byte[] clientID, String savePath, int intervalUpdateListPeers, int blockSize, float thresholdEndGameTest) {

    	this.torrent = torrent;
    	this.blockSize = blockSize * 1024;
    	this.savePath = savePath;
    	this.clientID = clientID;
    	
    	this.intervalUpdateListPeers = intervalUpdateListPeers;
    	this.thresholdEndGameTest = thresholdEndGameTest;
    	
    	this.initialization();
    }
    
    /**
     * Saves the information of pieces, sent or obtained, from different peers of the swarm.
     * This information will be showed by logs.
     * @param save boolean
     */
    public void setSaveInformationPieceTransfer(boolean save){
    	this.saveInformationPieceTransfer = save;
    }
    
    
    private void initialization(){
    	
    	this.runBlockUntil = false;
    	
        this.peerList = new LinkedHashMap<String, Peer>();
        this.peerIdTrackerList = new LinkedHashMap<String, Peer>();
        this.peerIPListeningPortList = new LinkedHashMap<String, Peer>();
        this.task = new TreeMap<String, DownloadTask>();
        this.peerAvailabilies = new LinkedHashMap<String, BitSet>();
        
        this.nbPieces = torrent.piece_hash_values_as_binary.size();
        this.pieceList = new Piece[this.nbPieces];
        this.nbOfFiles = this.torrent.length.size();

        this.isComplete = new BitSet(nbPieces);
        this.isActiveRequests = new BitSet(nbPieces);
        this.isRequested = new BitSet(nbPieces);
        this.isPieceSent = new BitSet(nbPieces);
        this.output_files = new RandomAccessFile[this.nbOfFiles];

        this.length = this.torrent.total_length;
        this.left = this.length;
        
    	this.checkTempFiles();
    	
    	//For logs
    	this.peerLogsPiecesTransferred = new LinkedHashMap<String, Peer>();
    	this.numPieceSent = 0;
    	
        int file = 0;
        int fileoffset = 0;
        
        logger.info("Checking in cache ... ");
        logger.info("Length of Piece: " + this.torrent.pieceLength);
        if(this.blockSize > this.torrent.pieceLength){
        	this.blockSize = this.torrent.pieceLength;
        }
        logger.info("Block Size of File (" + (this.blockSize/1024) + " * 1024): " + this.blockSize);
        
        PeerProtocol.BLOCK_SIZE = this.blockSize;
        
        /**
         * Construct all the pieces with the correct length and hash value
         */
        for (int i = 0; i < this.nbPieces; i++) {
            TreeMap<Integer, Integer> tm = new TreeMap<Integer, Integer>();
            int pieceoffset = 0;
            
            do {
                tm.put(file, fileoffset);
                if (fileoffset + this.torrent.pieceLength - pieceoffset >=
                    (Integer) (torrent.length.get(file).intValue()) &&
                    i != this.nbPieces - 1) {
                    pieceoffset += ((Integer) (torrent.length.get(file).intValue())).
                            intValue() - fileoffset;
                    file++;
                    fileoffset = 0;
                    if (pieceoffset == this.torrent.pieceLength)
                        break;
                } else {
                    fileoffset += this.torrent.pieceLength - pieceoffset;
                    break;
                }
            } while (true);
            
            pieceList[i] = new Piece(i,
                                     (i != this.nbPieces - 1) ?
                                     this.torrent.pieceLength :
                                     ((Long) (this.length %
                                              this.torrent.pieceLength)).
                                     intValue(), this.blockSize, (byte[]) torrent.
                                     piece_hash_values_as_binary.get(i), tm);
            
            
            if (this.testComplete(i)) {
                this.setComplete(i, true);
                this.left -= this.pieceList[i].getLength();
            }
            
        }
        
        this.setInitTimeStartedProtocol(System.currentTimeMillis());
        
        //logger.info("List of Complete Pieces: "+pieceComplete);
        float totaldl = (float) (((float) (100.0)) * ((float) (this.isComplete.cardinality())) / ((float) (this.nbPieces)));
        logger.info("Num Pieces in cache: " + this.isComplete.cardinality() + " of " + this.nbPieces + " - " + totaldl + "%" );
        this.lastUnchoking = System.currentTimeMillis();
        
    }
    
    
    public boolean testComplete(int piece) {
    	
        boolean complete = false;
        this.pieceList[piece].setBlock(0, this.getPieceFromFiles(piece));
        complete = this.pieceList[piece].verify();
        this.pieceList[piece].clearData();
        return complete;
    }
    
    /**
     * Periodically call the unchokePeers method. This is an infinite loop.
     * User have to exit with Ctrl+C, which is not good... Todo is change this
     * method...
     */
    public void blockUntilCompletion() {
        byte[] b = new byte[0];

        
        while (runBlockUntil) {
            try {
                synchronized (b) {
                    b.wait(10000);
                    this.unchokePeers();
                    b.notifyAll();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            if (this.isComplete() && runBlockUntil){
            	//logger.info("Sharing... Press Ctrl+C to stop client: "+savePath);
            }
        }
    }
    
    
    
    public void run() {
    	runBlockUntil = true;
        blockUntilCompletion();
    }
    
   
    /**
     * Displays the number of pieces, that have been transferred to other peers, 
     * and/or the pieces obtained from other peers.
     */
    public void displayLogs(){
    	
    	if(this.task_duration > 0){
    		String duration = String.format("%d m. %d s.", 
    			    TimeUnit.MILLISECONDS.toMinutes(task_duration),
    			    TimeUnit.MILLISECONDS.toSeconds(task_duration) - 
    			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(task_duration))
    			);
        	
        	logger.info("INIT LOG TORRENT_DOWNLOAD_CLIENT");
    		logger.info("TORRENT_DOWNLOAD COMPLETED ... " + duration + " (" + task_duration + " milliseconds)" );
    		logger.info("(Init Time: " + this.initTime + ") - (End Time: " + this.endTime + ")");
    		
    		float total;
    		for(Peer p: this.peerLogsPiecesTransferred.values()){
    			String ipLp = "";
    			if(p.getListeningPort()!=-1){
    				ipLp = p.getIP() + ":" + p.getListeningPort();
    			}else{
    				ipLp = p.getIP() + ":" + p.getPort();
    			}
    			total = (float) (((float) (100.0)) * (p.getNumPiecesDownloadedByThis()) / ((float) (this.nbPieces)));
        		logger.info("Total of pieces from Peer (" + ipLp + "): " + total + "%");
    		}
    		
    		logger.info("END LOG TORRENT_DOWNLOAD_CLIENT");
    	}
		
		logger.info("INIT LOG TORRENT_DOWNLOAD_SEED");
		logger.info("Total of blocks sent to Peers: " + this.numPieceSent);
		if(this.numPieceSent > 0){
			float tc;
			String ipLp = "";
			for(Peer p: this.peerLogsPiecesTransferred.values()){
				ipLp = "";
				if(p.getNumPiecesSentToThis() > 0){

					if(p.getListeningPort()!=-1){
						ipLp = p.getIP() + ":" + p.getListeningPort();
					}else{
						ipLp = p.getIP() + ":" + p.getPort();
					}
					
					tc = (float) (((float) (100.0)) * (p.getNumPiecesSentToThis()) / ((float) (this.numPieceSent)));
		    		logger.info("Total of blocks sent to Peer (" + ipLp + "): " + tc + "%");
				}
				
			}
		}
		logger.info("END LOG TORRENT_DOWNLOAD_SEED");
    	
    }
    
    
    public void stopBlockUntilCompletion(){
    	runBlockUntil = false;
    }
    
    public void stopAndClearActiveTask(){
    	try{
    		if(!this.task.isEmpty()){
        		for(DownloadTask dt:this.task.values()){
        			logger.warn("stopAndClearActiveTask -> DownloadTask end() " + dt.getIdTask());
        			dt.end();
        		}
        		this.task.clear();
        	}
        }catch(NullPointerException ex){
    		logger.error("stopAndClearActiveTask exception: " + ex);
    	}catch(Exception e){
    		logger.error("stopAndClearActiveTask exception: " + e);
    	}
    }
    
    public boolean isRunning(){
    	return runBlockUntil;
    }
    
    /**
     * Create and start the peer updater to retrieve new peers sharing the file
     */
    public void startTrackerUpdate() {
        this.pu = new PeerUpdater(this.clientID, this.torrent, this.intervalUpdateListPeers);
        this.pu.addPeerUpdateListener(this);
        this.pu.setListeningPort(this.cl.getConnectedPort());
        this.pu.setIpAddress(this.cl.getIPaddress());
        this.pu.setLeft(this.left);
        this.pu.start();
    }

    /**
     * Stop the tracker updates
     */
    public void stopTrackerUpdate() {
        this.pu.end();
    }

    /**
     * Create the ConnectionListener to accept incoming connection from peers
     * @param minPort The minimal port number this client should listen on
     * @param maxPort The maximal port number this client should listen on
     * @return True if the listening process is started, false else
     */
    public boolean startListening(int minPort, int maxPort) {

        this.cl = new ConnectionListener();
        if (this.cl.connect(minPort, maxPort)) {
            this.cl.addConListenerInterface(this);
            return true;
        } else {
        	logger.warn("Could not create listening socket...");
            return false;
        }
    }

    /**
     * Close all open files
     */
    public void closeTempFiles() {
        for (int i = 0; i < this.output_files.length; i++)
            try {
                this.output_files[i].close();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    /**
     * Check the existence of the files specified in the torrent and if necessary,
     * create them
     *
     * @return int
     * @todo Should return an integer representing some error message...
     */
    public synchronized int checkTempFiles() {
    	String saveas = savePath;
        //Constants.SAVEPATH; // Should be configurable

        if (this.nbOfFiles > 1) {
            saveas += this.torrent.saveAs + "/";
        }
        
        boolean dir = false;
        File f = new File(saveas);
        if(saveas.substring(saveas.length()-1).equals("/")){
        	if(!f.exists()){
        		f.mkdirs();
        	}
        	dir = true;
        }else if(!f.exists()){
        	try {
				f.createNewFile();
			} catch (IOException e) {
				logger.error("Could not create temp file: " + e);
			}
        }
        
        for (int i = 0; i < this.nbOfFiles; i++) {
        	
        	File temp;
        	
        	if(dir){
        		temp = new File(f.getPath()+"/" + ((String) (this.torrent.name.get(i))));
        	}else{
        		temp = f;
        	}
        	//logger.info(" checkTemp Files PATH TEMPFILE: "+temp.getAbsolutePath());
            try {
                this.output_files[i] = new RandomAccessFile(temp, "rw");
                this.output_files[i].setLength((Integer)this.torrent.length.get(i).intValue());
            } catch (IOException ioe) {
            	logger.error("Could not create temp files: " + ioe);
                ioe.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * Save a piece in the corresponding file(s)
     * @param piece int
     */
    public synchronized void savePiece(int piece) {
    	
        //int remaining = this.pieceList[piece].getLength();
        byte[] data = this.pieceList[piece].data();
        int remainingData = data.length;
        for (@SuppressWarnings("unchecked")
		Iterator<Integer> it = this.pieceList[piece].getFileAndOffset().keySet().iterator(); it.hasNext(); ) {
            try {
                Integer file = it.next();
                int remaining = ((Integer)this.torrent.length.get(file).intValue()).
                                intValue()
                                -
                                ((Integer) (this.pieceList[piece].
                                            getFileAndOffset().
                                            get(file))).intValue();
                this.output_files[file.intValue()].seek(((Integer)
                        (this.pieceList[piece].getFileAndOffset().get(file))).
                        intValue());
                this.output_files[file.intValue()].write(data,
                        data.length - remainingData,
                        (remaining < remainingData) ? remaining : remainingData);
                remainingData -= remaining;
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }
        }
        
        data = null;
        this.pieceList[piece].clearData();
    }

    /**
     * Save the downloaded files into the corresponding directories
     * @deprecated
     */
    public synchronized void save() {
        synchronized (this) {
        	try{
        		synchronized (this.isComplete) {
                    byte[] data = new byte[0];
                    for (int i = 0; i < this.nbPieces; i++) {
                        if (this.pieceList[i] == null) {

                        } else {
                            data = Utils.concat(data, this.pieceList[i].data());
                        }
                    }
                    String saveAs = savePath;//Constants.SAVEPATH;
                    int offset = 0;
                    if (this.nbOfFiles > 1)
                        saveAs += this.torrent.saveAs + "/";
                    for (int i = 0; i < this.nbOfFiles; i++) {
                        try {
                            new File(saveAs).mkdirs();
                            FileOutputStream fos = new FileOutputStream(saveAs +
                                    ((String) (this.torrent.name.get(i))));
                            fos.write(Utils.subArray(data, offset,
                                                     ((Integer) (this.torrent.
                                    length.get(i).intValue()))));
                            fos.flush();
                            fos.close();
                            offset += ((Integer) (this.torrent.length.get(i).intValue()));
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                            System.err.println("Error when saving the file " + ((String) (this.torrent.name.get(i))));
                        }
                    }
                }
        		
        	}catch(NullPointerException ex){
        		logger.error("save exception: " + ex);
        	}catch(Exception e){
        		logger.error("save exception: " + e);
    	
        	}
        }
    }

    /**
     * Check if the current download is complete
     * @return boolean
     */
    public synchronized boolean isComplete() {
        synchronized (this.isComplete) {
            return (this.isComplete.cardinality() == this.nbPieces);
        }
    }

    /**
     * Returns the number of pieces currently requested to peers
     * @return int
     */
    public synchronized int cardinalityActiveRequests() {
        return this.isActiveRequests.cardinality();
    }
    
    /**
     * Returns the number of pieces, that have been requested at least once.
     * @return int
     */
    public synchronized int cardinalityRequested() {
        return this.isRequested.cardinality();
    }
    
    /**
     * Returns the number of pieces sender to peers
     * @return int
     */
    public synchronized int cardinalitySender() {
        return this.isPieceSent.cardinality();
    }

    /**
     * Returns the piece with the given index
     * @param index The piece index
     * @return Piece The piece with the given index
     */
    public synchronized Piece getPiece(int index) {
        synchronized (this.pieceList) {
            return this.pieceList[index];
        }
    }

    /**
     * Check if the piece with the given index is complete and verified
     * @param piece The piece index
     * @return boolean
     */
    public synchronized boolean isPieceComplete(int piece) {
        synchronized (this.isComplete) {
            return this.isComplete.get(piece);
        }
    }

    /**
     * Check if the piece with the given index is requested by a peer
     * @param piece The piece index
     * @return boolean
     */
    public synchronized boolean isPieceSent(int piece) {
        synchronized (this.isPieceSent) {
            return this.isPieceSent.get(piece);
        }
    }
    
    /**
     * Checks if the piece, with the given index, is being transferred 
     * by a peer in this moment.
     * @param piece The piece index
     * @return boolean
     */
    public synchronized boolean isPieceActiveRequest(int piece) {
        synchronized (this.isActiveRequests) {
            return this.isActiveRequests.get(piece);
        }
    }
    
    /**
     * Checks if the piece, with the given index, has been requested at least once.
     * @param piece
     * @return boolean
     */
    public synchronized boolean isPieceRequested(int piece) {
        synchronized (this.isRequested) {
            return this.isRequested.get(piece);
        }
    }
    
    
    /**
     * Mark a piece as complete or not according to the parameters
     * @param piece The index of the piece to be updated
     * @param is True if the piece is now complete, false otherwise
     */
    public synchronized void setComplete(int piece, boolean is) {
        synchronized (this.isComplete) {
            this.isComplete.set(piece, is);
        }
    }

    /**
     * Marks a piece, as currently requested or not, according to the parameters.
     * @param piece int The index of the piece.
     * @param is boolean True if the piece is being requested, false if otherwise.
     */
    public synchronized void setActiveRequest(int piece, boolean is) {
        synchronized (this.isActiveRequests) {
            this.isActiveRequests.set(piece, is);
        }
    }
    
    /**
     * Mark a piece as requested or not according to the parameters
     * @param piece The index of the piece to be updated
     * @param is True if the piece is now requested, false otherwise
     */
    public synchronized void setRequested(int piece, boolean is) {
        synchronized (this.isRequested) {
            this.isRequested.set(piece, is);
        }
    }
    
    /**
     * Mark a piece as requested or not according to the parameters
     * @param piece The index of the piece to be updated
     * @param is True if the piece is now requested, false otherwise
     */

    public synchronized void setSent(int piece, boolean is) {
        synchronized (this.isPieceSent) {
            this.isPieceSent.set(piece, is);
        }
    }

    /**
     * Returns a String representing the piece being requested by peers.
     * Used only for pretty-printing.
     * @return String
     */
    public synchronized String requestedBits() {
        String s = "";
        synchronized (this.isActiveRequests) {
            for (int i = 0; i < this.nbPieces; i++)
                s += this.isActiveRequests.get(i) ? 1 : 0;
        }
        return s;
    }

    /**
     * Returns the index of the piece that can be downloaded by the peer 
     * with the identifier specified by the parameter. By default Rarest First Strategy 
     * is applied, that is to say, it is given priority to pieces less common 
     * (it is chosen the piece that the number of peers which they have 
     * is lower than the rest of the pieces). If the parameter endGamStrategy is true, 
     * will give priority to more common pieces.
     * @param id String the identifier of the peer, at which will be requested the piece. 
     * @param endGameStrategy boolean
     * @return int the index of the piece to request.
     */
    private synchronized int choosePiece2Download(String id, boolean endGameStrategy) {
        
    	ArrayList<Integer> possible = new ArrayList<Integer>(this.nbPieces);
        int index = 0;
    	synchronized (this.isComplete) {
            for (int i = 0; i < this.nbPieces; i++) {
                if ((!this.isPieceActiveRequest(i) || 
                		(this.isComplete.cardinality() > this.nbPieces - 3) || endGameStrategy) && 
                		(!this.isPieceComplete(i)) && this.peerAvailabilies.get(id) != null) {
                    if (this.peerAvailabilies.get(id).get(i))
                        possible.add(i);
                }
            }
        }
    	
    	
    	if(possible.size() > 0){
    		
    		
    		if(this.peerAvailabilies.size() > 1 && !endGameStrategy){
    			
    			Collections.shuffle(possible);
    			try{
    				Collections.sort(possible, new RarestFirstComparator(this.peerAvailabilies));
    			}catch(Exception e){
    				logger.warn("Collections Sort RaresFirstCompartator: " + e);
    			}
    			
    			index = possible.get(0);
    			this.setActiveRequest(index, true);
    			this.setRequested(index, true);
    			return index;
       			 
            }else if(this.peerAvailabilies.size() > 1 && endGameStrategy){
            	
            	Collections.shuffle(possible);
            	try{
            		Collections.sort(possible, new PopularFirstComparator(this.peerAvailabilies));
    			}catch(Exception e){
    				logger.warn("Collections Sort PopularFirstComparator: " + e);
    			}
            	index = possible.get(0);
    			this.setActiveRequest(index, true);
    			this.setRequested(index, true);
    			return index;
           	 
            }else{
           	 
           	 	Random r = new Random(System.currentTimeMillis());
                index = possible.get(r.nextInt(possible.size()));
                this.setActiveRequest(index, true);
                this.setRequested(index, true);
                return (index);
                
            }
       	
       }
       
       return -1;
            
            
    }

    /**
     * Removes a task and peer after the task sends a completion message.
     * Completion can be caused by an error (bad request, ...) or simply by the
     * end of the connection
     * @param dt {@link DownloadTask}
     * @param reason Reason of the completion
     */
    public synchronized void taskCompleted(DownloadTask dt, int reason) {
    	
    	if(this.runBlockUntil){
    		
    		String reasonString = "";
            switch (reason) {
            case DownloadTask.CONNECTION_REFUSED:
            	reasonString = "Connection refused by host ";
                break;
            case DownloadTask.MALFORMED_MESSAGE:
            	reasonString = "Malformed message from ";
                break;
            case DownloadTask.UNKNOWN_HOST:
            	reasonString = "Connection could not be established to ";
            	break;
            case DownloadTask.BAD_HANDSHAKE:
            	reasonString = "Wrong file id ";
            	break;
            case DownloadTask.CONNECTION_ALREADY_EXIST:
            	reasonString = "Connection Already Exist ";
            	break;
            }
            
            
            String ipLp="";
            if(dt.peer.getListeningPort() != -1 && dt.peer.getPort() != dt.peer.getListeningPort()){
            	ipLp = " / " + dt.peer.getListeningPort();
            }
            
            if(dt.peer.getIDTracker() != null){
            	logger.warn("Remove (" + dt.getIdTask() + ipLp + " / " + dt.peer.getIDTracker() + ") of peerAvailabilies/peerList/task, reason: "+reasonString);
            }else{
            	logger.warn("Remove (" + dt.getIdTask() + ipLp + " ) of peerAvailabilies/peerList/task, reason: "+reasonString);
            }
            
            
            this.peerAvailabilies.remove(dt.peer.toString());
        	this.peerList.remove(dt.peer.toString());
        	if(dt.peer.getIDTracker()!=null){
        		synchronized (this.peerIdTrackerList) {
    				this.peerIdTrackerList.remove(dt.peer.getIDTracker());
    				if(dt.peer.getListeningPort()!=-1){
    					ipLp = (dt.peer.getIP() + ":" + dt.peer.getListeningPort());
	    				this.peerIPListeningPortList.remove(ipLp);
    	        	}
    			}
        	}
        	
        	synchronized (this.task) {
        		this.task.remove(dt.peer.toString());
        	}
    		
    	}
    	
    }
    
    /**
     * Save the last time, when receives a new piece.
     */
    public synchronized void receivePieceBlock(String peerID){
    	this.lastTimeReceivePieceBlock = System.currentTimeMillis();
    	
    }
    
    /**
     * Received when a piece has been fully downloaded by a task. The piece might
     * have been corrupted, in which case the manager will request it again later.
     * If it has been successfully downloaded and verified, the piece status is
     * set to 'complete', a 'HAVE' message is sent to all connected peers and the
     * piece is saved into the corresponding file(s)
     * @param peerID String
     * @param i int
     * @param complete boolean
     */
    public synchronized void pieceCompleted(String peerID, int i, boolean complete, DownloadTask dt) {
    	
    	synchronized (this.isActiveRequests) {
            this.isActiveRequests.clear(i);
        }
      
        if (complete && !this.isPieceComplete(i)) {
        	
            pu.updateParameters(this.torrent.pieceLength, 0, "");
            
            synchronized (this.isComplete) {
            	
            	this.isComplete.set(i, complete);
            	
            	float totaldl = (float) (((float) (100.0)) * ((float) (this.isComplete.cardinality())) / ((float) (this.nbPieces)));
            	
            	String ipLp="";
            	if(dt.peer.getListeningPort() != -1 && dt.peer.getPort() != dt.peer.getListeningPort()){
                	ipLp = " / " + dt.peer.getListeningPort();
                }
            	
            	if(dt.peer.getIDTracker()!=null){
            		logger.info(i + " Completed by Peer ID (" + dt.getIdTask() + ipLp + " / " + dt.peer.getIDTracker() + ") - (Total dl = " + totaldl +"% )");
            	}else{
            		logger.info(i + " Completed by Peer ID (" + dt.getIdTask() + ipLp + " ) - (Total dl = " + totaldl +"% )");
            	}
            	
            	if(this.saveInformationPieceTransfer){
            		
            		int nDownload, nSent;
            		String key = dt.peer.getIDTracker() + dt.peer.getIP();
            		if(this.peerLogsPiecesTransferred.containsKey(key)){
            			nDownload = this.peerLogsPiecesTransferred.get(key).getNumPiecesDownloadedByThis() + 1;
            			nSent = this.peerLogsPiecesTransferred.get(key).getNumPiecesSentToThis();
            		}else{
            			nDownload = dt.peer.getNumPiecesDownloadedByThis() + 1;
            			nSent = dt.peer.getNumPiecesSentToThis();
            		}
            		dt.peer.setNumPiecesDownloadedByThis(nDownload);
            		dt.peer.setNumPiecesSentToThis(nSent);
            		this.peerLogsPiecesTransferred.put(key, dt.peer);
            	}
            	
            }
            
            try{ 
            	synchronized (this.task) {
                	for(DownloadTask selectDT: this.task.values()){
                    	try {
                    		if(selectDT.ms != null)
                    			selectDT.ms.addMessageToQueue(new Message_PP(PeerProtocol.HAVE,Utils.intToByteArray(i), 1));
                    	} catch (NullPointerException npe) {}
                    }
                }
            }catch(NullPointerException ex){
            	logger.error("pieceCompleted " +i + "/"+ peerID + " exception: " + ex);
            }catch(Exception e){
            	logger.error("pieceCompleted " +i + "/"+ peerID + " exception: " + e);
	
            }
            
            this.savePiece(i);
            this.getPieceBlock(i, 0, 15000);
            
            
            synchronized (this.isComplete) {
            	
            	if (this.isComplete.cardinality() == this.nbPieces) {
            		
                    long  endTimeEndedProtocol = System.currentTimeMillis();
                    this.task_duration = endTimeEndedProtocol - this.initTimeStartedProtocol;
                    
                    if(this.initTime == null){
                    	DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTimeInMillis(this.initTimeStartedProtocol);
                        this.initTime = dateFormat.format(calendar.getTime());
                        calendar.setTimeInMillis(endTimeEndedProtocol);
                        this.endTime = dateFormat.format(calendar.getTime());
                    }
            		
                    if(this.downloadManagerListener != null) {
                        this.downloadManagerListener.downloadComplete();
                        this.pu.completed(); 
                    }
                    this.notify();
                }
            }
            
            
        } else {
            //this.pieceList[i].data = new byte[0];
        }
        
    }

    /**
     * Set the status of the piece to requested or not
     * @param i int
     * @param requested boolean
     */
    public synchronized void pieceRequestActive(int i, boolean requested) {
        this.isActiveRequests.set(i, requested);
    }

    
    /**
     * Choose which of the connected peers should be unchoked and authorized to
     * upload from this client. A peer gets unchoked if it is not interested, or
     * if it is interested and has one of the 5 highest download rate among the
     * interested peers. Every 3 times this method is called, calls the
     * optimisticUnchoke method, which unchoke a peer no matter its download rate,
     * in a try to find a better source.
     * Checks not interested peers to send a message NOT_INTERESTED and peers 
     * of interest to send a message INTERESTED.
     * 
     */
	private synchronized void unchokePeers() {
    	
    	logger.info("UnchokePeers ... ");
    	
    	//For logs
    	String ips = "";
    	String downloaders = "";
    	
    	if(!this.pu.isEnd() && this.runBlockUntil){
    		
    		try{ 
    			synchronized (this.task) {
                	
                    int nbNotInterested = 0;
                    int nbDownloaders = 0;
                    int nbChoked = 0;
                    int nbInterestedAndNotChoked = 0;
                    
                     
                    
                    this.unchoken.clear();
                    LinkedList<Peer> l = new LinkedList<Peer>(this.peerList.values());
                    
                    if(!this.isComplete()){	//logger.info("UNCHOKE PEERS SORT COMPARATOR - DL_RATE");
                    	Collections.sort(l, new DLRateComparator());
    	            }else {	//logger.info("UNCHOKE PEERS SORT COMPARATOR - UL_RATE");
                    	Collections.sort(l, new ULRateComparator());
                    }
                    
                    LinkedList<DownloadTask> listInterestingHavePiece = new LinkedList<DownloadTask>();
                    DownloadTask dt;
                    
                    for (Iterator<Peer> it = l.iterator(); it.hasNext(); ) {
                    	
                        Peer p = it.next();
                        dt = this.task.get(p.toString());
                        
                        int nPieceInterest = 0;
                        synchronized (this.isComplete) {
                        	 if(!this.isComplete() && this.peerAvailabilies!=null && !this.peerAvailabilies.isEmpty() && this.peerAvailabilies.containsKey(p.toString())){
                             	BitSet interest = (BitSet) (this.peerAvailabilies.get(p.toString()).clone());
                             	interest.andNot(this.isComplete);
                             	nPieceInterest = interest.cardinality();
                             }
    					}
                        //UnInteresting
                    	if(nPieceInterest == 0 && dt!=null && dt.peer.isInteresting()){
                    			if(dt.ms != null){
                    				dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.NOT_INTERESTED, 2));
                                    dt.peer.setInteresting(false);
                                    dt.peer.setChoking(true);
                    			}
                    	}else if(nPieceInterest > 0 && dt!=null && !dt.peer.isInteresting()) {
                    	//Interesting
                    		listInterestingHavePiece.add(dt);
                    	}
                       
                        
                        if(this.peerAvailabilies!=null && this.peerAvailabilies.containsKey(p.toString())){
                        	ips = ips + " - ( "+p.toString()+" / "+ nPieceInterest +" - ";
                        	
                        	 if(!this.isComplete() && dt != null){
                             	ips = ips + "dlRate: " + dt.peer.getDLRate(false) / (1024 * 10) + " ko/s" + ")";
                             }else if(dt != null){
                            	 ips = ips + "ulRate: " + dt.peer.getULRate(false) / (1024 * 10) + " ko/s" + ")";
                             }
                        	
                        }else{
                        	ips = ips + " - ( "+p.toString()+" / this.peerAvailabilies not key)";
                        }
                        
                        if(dt != null){
                        	
                        	if (nbDownloaders < 5 && dt != null) {
                            	
                                if (!dt.peer.isInterested()) {
                                	
                                    if (dt.peer.isChoked()){
                                    	
                                    	if(dt.ms != null){
                                    		this.unchoken.put(dt.peer.toString(), dt.peer);
                                            dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.UNCHOKE));
                                            dt.peer.setChoked(false);
                                            while (this.unchokeList.remove(dt.peer));
                                    	}
                                    	
                                    }
                                    
                                    nbNotInterested++;
                                    
                                } else if (dt.peer.isChoked()) {
                                	
                                	if(dt.ms != null){
                                		this.unchoken.put(dt.peer.toString(), dt.peer);
                                        dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.UNCHOKE));
                                        dt.peer.setChoked(false);
                                        while (this.unchokeList.remove(dt.peer));
                                        nbDownloaders++;
                                        downloaders = downloaders + " - ("+dt.getIdTask()+")";
                                	}
                                	
                                } else {
                                	
                                	if(dt.ms != null){
                                		
                                		nbInterestedAndNotChoked++;
                                    	nbDownloaders++;
                                    	downloaders = downloaders + " - ("+dt.peer.toString()+")";
                                	}
                                }
                                
                            } else {
                            	
                            	if(dt == null){
                            		logger.warn("NOT DOWNLOADTASK clientIP: "+p.toString());
                            	}
                            	
                                if (dt!=null && dt.ms != null && ( !dt.peer.isChoked() || (dt.peer.isChoked() && !this.unchokeList.contains(dt.peer) && dt.peer.isInterested()) )) {
                                	
                                    dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.CHOKE));
                                    dt.peer.setChoked(true);
                                    
                                    if (!this.unchokeList.contains(dt.peer))
                                        this.unchokeList.add(dt.peer);
                                    
                                    nbChoked++;
                                    
                                }
                            }
                        }
                    
                        p = null;
                        dt = null;
                    }
                    
                    //DISPLAY LOGS
                    logger.info("UNCHOKEPEERS FUNCTION - listPeers "+this.peerList.values().size()+": "+ips);
                    logger.info("nbNotInterested: "+nbNotInterested);
                	logger.info("nbInterestedAndNotChoked: "+nbInterestedAndNotChoked);
                    logger.info("nbDownloaders: "+nbDownloaders);
                    if(downloaders != ""){
                    	logger.info("Downloaders: "+downloaders);
                    }
                	logger.info("nbChoked: "+nbChoked);
                	
                    if(!listInterestingHavePiece.isEmpty()){
                    	logger.info("UnchokePeers execute PeerAvailability num peers ... "+listInterestingHavePiece.size());
                    	for(DownloadTask tdI:listInterestingHavePiece){
                    		synchronized (tdI.peer) {
                    			this.peerAvailability(tdI.peer.toString(), tdI.peer.getHasPiece());
    						}
                    	}
                    }
                }
        		
                this.lastUnchoking = System.currentTimeMillis();
                if (this.optimisticUnchoke-- == 0) {
                    this.optimisticUnchoke();
                    this.optimisticUnchoke = 3;
                }
    			
    			
            }catch(NullPointerException ex){
            	logger.error("unchokePeers exception: " + ex);
            }catch(Exception e){
            	logger.error("unchokePeers exception: " + e);
	
            }
            
    	}
    		
    }
	
    @SuppressWarnings("unused")
	private int getNumPieceInterest(DownloadTask dt){
    	
    	int nPieceInterest = 0;
    	if(!this.isComplete() && this.peerAvailabilies!=null && !this.peerAvailabilies.isEmpty() && this.peerAvailabilies.containsKey(dt.peer.toString())){
			BitSet interest = (BitSet) (this.peerAvailabilies.get(dt.peer.toString()).clone());
         	interest.andNot(this.isComplete);
         	nPieceInterest = interest.cardinality();
         }
    	
    	return nPieceInterest;
    }

    /**
     * Chooses, randomly, a peer between that are interested and are choked, 
     * in order that this pass to be unchocked.  In this way, makes the peers 
     * that at the start don’t have pieces to share, can obtain its first pieces.
     */
    private synchronized void optimisticUnchoke() {
    	
    	logger.info("Optimistically unchoken...");
    	
        if (!this.unchokeList.isEmpty()) {
        	
            DownloadTask dt = null;
            synchronized (this.task) {
       		 
        		LinkedList<DownloadTask> listCandidatePeers = new LinkedList<DownloadTask>();
        		LinkedList<Peer> listRemove = new LinkedList<Peer>();
        		String candidates = "";
        		DownloadTask dtCandidate = null;
        		//int nPieceInterestFromCandidate = 0;
        		for(Peer pU: this.unchokeList){
        			
        			dt = this.task.get(pU.toString());
        			
        			if(dt != null && dt.peer.isInterested()){
        				
        				listCandidatePeers.add(dt);
        				candidates = candidates + " - ("+dt.getIdTask()+")";
        				
        				/*if(!this.isComplete() && (dtCandidate == null || this.getNumPieceInterest(dt) > nPieceInterestFromCandidate)){
        					
        					dtCandidate = dt;
        					nPieceInterestFromCandidate = this.getNumPieceInterest(dtCandidate);
        					
        				}*/
        				
        			}else if(dt == null){
        				listRemove.add(pU);
        				logger.warn("NOT DOWNLOADTASK clientIP: "+pU.getIP());
        			}
        		}
        		
        		for(Peer p: listRemove){
        			this.unchokeList.remove(p);
        		}
        		
        		if(!listCandidatePeers.isEmpty()){
        			
        			/*logger.info("Candidates: " + candidates);
        			if(dtCandidate != null){
        				logger.info(dtCandidate.getIdTask() + " is Candidate with " + nPieceInterestFromCandidate + " pieces interest");
        			}*/
        			
        			while(dtCandidate == null && !listCandidatePeers.isEmpty()){
        				Random r = new Random(System.currentTimeMillis());
        				dtCandidate = listCandidatePeers.get(r.nextInt(listCandidatePeers.size()));
                		this.unchokeList.remove(dtCandidate.peer);
                		if(dtCandidate.ms == null){
                			listCandidatePeers.remove(dtCandidate);
                			dtCandidate = null;
                		}
        			}
        			
                   
                    if (dtCandidate != null) {
                    	dtCandidate.ms.addMessageToQueue(new Message_PP(PeerProtocol.UNCHOKE));
                        //logger.info("Optimistically Send UNCHOKE (setChoked(false)): " + dtCandidate.getIdTask());
                        synchronized (dtCandidate.peer) {
                        	dtCandidate.peer.setChoked(false);
						}
                        this.unchoken.put(dtCandidate.peer.toString(), dtCandidate.peer);
                    } 
                    
        		}
        		
            }
            	
        }
    }

    /**
     * Received when a task is ready to download or upload. In such a case, if
     * there is a piece that can be downloaded from the corresponding peer, then
     * request the piece. 
     * 
     * In the event that the End Game mode is active and the current selected 
     * piece is already downloaded, it will be contacted with the rest of tasks 
     * that are waiting for the download of this same piece, to cancel the process.  
     * In other words, that send cancellation messages and start the process 
     * of selection of pieces.
     * 
     * If the peer doesn’t have interesting pieces, it is sent 
     * a not interested message.
     * 
     * @param peerID String (peer.IP:peer.Port)
     */
    public synchronized void peerReady(String peerID) {
    	
    	synchronized (this) {
    		
    		if(this.runBlockUntil){
    			
    			try{
            		
            		synchronized (this.task) {
            			
            			if (System.currentTimeMillis() - this.lastUnchoking > 10000)
            	            this.unchokePeers();
            			
            	    	DownloadTask dt = null;
            	    	int piece2request = -1;
            			
                		
                		if(this.task.containsKey(peerID)){
                			
                        	dt = this.task.get(peerID);
                        	
                        	float totaldl = ((float) (((float) (100.0)) *((float) (this.isComplete.cardinality())) /((float) (this.nbPieces))));
                        	
                        	if(!this.isComplete() && ( (this.thresholdEndGameTest != -1 && totaldl > this.thresholdEndGameTest) 
                            		|| (this.isRequested.cardinality() == this.nbPieces) )){
                        		
                            	logger.info(peerID + " End Game peerReady ... isRequested.cardinality: " + this.isRequested.cardinality() + "/" + this.nbPieces);
                            	
                            	if(this.downloadEndGamePiece != null && !this.isPieceComplete(this.downloadEndGamePiece.getIndex())
                            			&& this.peerAvailabilies.get(peerID).get(this.downloadEndGamePiece.getIndex())){
                            		
                            		
                            		//logger.info(peerID + " downloadEndGamePiece: " + this.downloadEndGamePiece.getIndex());
                            		
                            		piece2request = this.downloadEndGamePiece.getIndex();
                            		dt.setStartedEndGame(this.downloadEndGamePiece);
                            		
                            	}else if(this.downloadEndGamePiece == null || (this.downloadEndGamePiece != null && this.isPieceComplete(this.downloadEndGamePiece.getIndex()))){

                            		if(this.downloadEndGamePiece != null){
                            			for(DownloadTask dwt: this.task.values()){
                                    		if(!dwt.peer.toString().equals(dt.peer.toString())){
                                    			if(dwt.getIndexDownloadPiece() == this.downloadEndGamePiece.getIndex()){
                                        			//logger.info(dwt.peer.toString() + " pushMessageCancelPieceEndGameStrategy ..." + this.downloadEndGamePiece.getIndex());
                                    				dwt.mr.pushMessageCancelPieceEndGameStrategy(this.downloadEndGamePiece.getIndex());
                                    			}
                            				}
                                        }
                            		}
                            		
                            		this.downloadEndGamePiece = null;
                            		piece2request = this.choosePiece2Download(peerID, true);
                            		
                            		if(piece2request != -1){
                            			this.downloadEndGamePiece = this.pieceList[piece2request];
                            			//logger.info(peerID + " downloadEndGamePiece: " + this.downloadEndGamePiece.getIndex());
                                		dt.setStartedEndGame(this.downloadEndGamePiece);
                            		}else{
                            			//logger.info(peerID + " downloadEndGamePiece: null");
                            			dt.setStartedEndGame(this.downloadEndGamePiece);
                            		}
                            		
                            	}else{
                            			
                            		dt.setStartedEndGame(null);
                            		piece2request = this.choosePiece2Download(peerID, false);
                        			//logger.info(peerID + " choosePiece2DownloadEndGamePiece: " + piece2request);
                            	}
                            	
                            }else{
                            	piece2request = this.choosePiece2Download(peerID, false);
                            }
                        	
                    		
                    		if (piece2request != -1 && !dt.isDownloadPiece()){
                        		
                            	dt.requestPiece(this.pieceList[piece2request]);
                            	
                            	if(dt!=null && !dt.peer.isInteresting() && dt.ms != null) {
                                	//logger.info("peerReady Send INTERESTED: " + dt.peer.toString());
                                	dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.INTERESTED, 2));
                                	dt.peer.setInteresting(true);
                            	}
                            	
                            }else if(piece2request == -1){
                            	
                            	if(dt!=null && dt.peer.isInteresting() && dt.ms != null){
                            		//logger.info("peerReady Send NOT_INTERESTED: " + dt.peer.toString());
                            		dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.NOT_INTERESTED, 2));
                            		dt.peer.setInteresting(false);
                            		dt.peer.setChoking(true);
                            	}
                            	
                            }
                        }
                	}
            		
            	}catch(NullPointerException ex){
            		logger.error("PeerReady " + peerID + " exception: " + ex);
            		ex.printStackTrace();
            	}catch(Exception e){
            		logger.error("PeerReady " + peerID + " exception: " + e);
            		e.printStackTrace();
            	}
    			
    		}
    		
		}
    	
    }

    /**
     * Received when a peer request a piece. If the piece is available (which
     * should always be the case according to Bittorrent protocol) and we are
     * able and willing to upload, the send the piece to the peer
     * @param peerID String (peer.IP:peer.Port)
     * @param piece int
     * @param begin int
     * @param length int
     * @param dt {@link DownloadTask}
     */
    public synchronized void peerRequest(String peerID, int piece, int begin, int length, DownloadTask dt) {
    	
        if (this.isPieceComplete(piece) && this.runBlockUntil) {
        	
        	this.setSent(piece, true);
        	
        	//logger.info("Send (" + piece + ", begin:" + begin + ") to: " + dt.getIdTask());
        	
        	
        	if(this.saveInformationPieceTransfer){
        		
        		this.numPieceSent++;
        		
        		int nSent, nDownload;
        		String key = dt.peer.getIDTracker() + dt.peer.getIP();
        		if(this.peerLogsPiecesTransferred.containsKey(key)){
        			nSent = this.peerLogsPiecesTransferred.get(key).getNumPiecesSentToThis() + 1;
        			nDownload = this.peerLogsPiecesTransferred.get(key).getNumPiecesDownloadedByThis();
        		}else{
        			nSent = dt.peer.getNumPiecesSentToThis() + 1;
        			nDownload = dt.peer.getNumPiecesDownloadedByThis();
        		}
        		dt.peer.setNumPiecesSentToThis(nSent);
        		dt.peer.setNumPiecesDownloadedByThis(nDownload);
        		this.peerLogsPiecesTransferred.put(key, dt.peer);
        	}
    		
        	this.setLastTimeSendPieceBlock(System.currentTimeMillis());
        	
            if (dt != null && dt.ms != null) {
                dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.PIECE, Utils.concat(Utils.intToByteArray(piece), Utils.concat(Utils.intToByteArray(begin),
                                                  this.getPieceBlock(piece, begin, length)))));
                dt.peer.setULRate(length);
            }
            
            this.pu.updateParameters(0, length, "");
            
        } else {
            try {

    			logger.warn("peerRequest -> DownloadTask end()" + dt.getIdTask());
                dt.end();
            } catch (Exception e) {}
            this.task.remove(peerID);
            if(dt.peer.getIDTracker()!=null){
            	this.peerIdTrackerList.remove(dt.peer.getIDTracker());
        	}
            if(dt.peer.getListeningPort()!=-1){
            	String ipLp = (dt.peer.getIP() + ":" + dt.peer.getListeningPort());
				this.peerIPListeningPortList.remove(ipLp);
        	}
            this.peerList.remove(peerID);
            this.unchoken.remove(peerID);
        }

    }

    /**
     * Load piece data from the existing files
     * @param piece int
     * @return byte[]
     */
    @SuppressWarnings("rawtypes")
	public synchronized byte[] getPieceFromFiles(int piece) {
        byte[] data = new byte[this.pieceList[piece].getLength()];
        int remainingData = data.length;
        for (Iterator it = this.pieceList[piece].getFileAndOffset().keySet().
                           iterator(); it.hasNext(); ) {
            try {
                Integer file = (Integer) (it.next());
                int remaining = ((Integer)this.torrent.length.get(file).intValue()).
                                intValue()
                                -
                                ((Integer) (this.pieceList[piece].
                                            getFileAndOffset().
                                            get(file))).intValue();
                this.output_files[file.intValue()].seek(((Integer)
                        (this.pieceList[piece].getFileAndOffset().get(file))).
                        intValue());
                this.output_files[file.intValue()].read(data,
                        data.length - remainingData,
                        (remaining < remainingData) ? remaining : remainingData);
                remainingData -= remaining;
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }
        }
        
        return data;
    }
    

    /**
     * Get a piece block from the existing file(s)
     * @param piece int
     * @param begin int
     * @param length int
     * @return byte[]
     */
    public synchronized byte[] getPieceBlock(int piece, int begin, int length) {
    	
    	return Utils.subArray(this.getPieceFromFiles(piece), begin, length);
    		
    }

    /**
     * Update the piece availabilities for a given peer
     * @param peerID String
     * @param has BitSet
     */
    public synchronized void peerAvailability(String peerID, BitSet has) {
    	
    	if(!this.peerAvailabilies.containsKey(peerID)){
    		logger.info("Add peer in peerAvailability list " + peerID);
    	}
    	
    	this.peerAvailabilies.put(peerID, has);
    	BitSet interest = (BitSet) (has.clone());
    	interest.andNot(this.isComplete);
        
    	synchronized (this.task) {
    		if(this.task.containsKey(peerID)){
        		
        		DownloadTask dt = this.task.get(peerID);
    			
    			if (interest.cardinality() > 0 && !dt.peer.isInteresting() && dt.ms != null) {
    				//logger.info("Send INTERESTED: "+dt.peer.toString());
    				dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.INTERESTED, 2));
    				dt.peer.setInteresting(true);
    			}
        	}
    	}
    }

    /**
     * Initiates a connection with the peer.
     * @param p {@link Peer}
     */
    public synchronized void connect(Peer p) {
    	
    	byte[] bitField = this.getBitField();
    	
    	synchronized (this.task) {
    		if (!this.task.containsKey(p.toString())) {
            	logger.info("Connect new ... "+p.toString());
            	DownloadTask dt = new DownloadTask(p, this.torrent.info_hash_as_binary, this.clientID, true, bitField);
            	dt.setDownloadManager(this);
                dt.addDTListener(this);
                dt.start();
            }
    	}
    }

    /**
     * Disconnect and stop task thread the peer.
     * @param p {@link Peer}
     */
    public synchronized void disconnect(Peer p) {
        DownloadTask dt = task.remove(p.toString());
        if (dt != null) {
            dt.end();
            dt = null;
        }
    }

    /**
     * Given the list in parameter, check if the peers are already present in
     * the peer list. If not, then add them and create a new task for them
     * @param list LinkedHashMap
     */
    @SuppressWarnings("rawtypes")
	public synchronized void updatePeerList(LinkedHashMap list) {
        //this.lastUnchoking = System.currentTimeMillis();
        synchronized (this.task) {
            //this.peerList.putAll(list);
            Set keyset = list.keySet();
            for (Iterator i = keyset.iterator(); i.hasNext(); ) {
                String key = (String) i.next();
                if (!this.task.containsKey(key) && !this.peerIPListeningPortList.containsKey(key)) {
                	//logger.info("UpdatePeerList... put "+key);
                    Peer p = (Peer) list.get(key);
                    this.peerList.put(p.toString(), p);
                    this.connect(p);
                }else{
                	//logger.info("UpdatePeerList... exists already "+key);
                }
            }
        }
    }

    /**
     * Called when an update try fail. At the moment, simply display a message
     * @param error int
     * @param message String
     */
    public void updateFailed(int error, String message) {
    	logger.error(message);
    }
    
    
	public void updatePeerListState(Map<String, Integer> mapState) {
    	if(mapState != null && this.downloadManagerListener!=null){
    		this.downloadManagerListener.updatePeerListState(mapState, this.lastTimeSendPieceBlock, this.lastTimeReceivePieceBlock, this.initTimeStartedProtocol);
    	}
	}
    
	/**
	 * Checks if the identifier of the peer in the tracker corresponds 
	 * to a previously established connection.
	 * @param idTracker String
	 * @param peer {@link Peer}
	 * @return boolean
	 */
    public synchronized boolean peerConnectionAlreadyExists(String idTracker, Peer peer){
    	
    	synchronized (this.peerIdTrackerList) {
    		
    		boolean exist = false;
    		
    		if(this.peerIdTrackerList.containsKey(idTracker) && this.peerIdTrackerList.get(idTracker).getIP().equals(peer.getIP())){
        		exist = true;
        		Peer p = this.peerIdTrackerList.get(idTracker);
        		if(peer.getListeningPort() != -1 && p.getListeningPort() == -1){
        			p.setListeningPort(peer.getListeningPort());
        		}
        		if(p.getListeningPort() != -1){
        			String ipLIP = p.getIP() + ":" + p.getListeningPort();
            		if(!this.peerIPListeningPortList.containsKey(ipLIP)){
            			this.peerIPListeningPortList.put(ipLIP, p);
            		}            		
        		}
        		
        		
        	}else{
        		this.peerIdTrackerList.put(idTracker, peer);
        		if(peer.getListeningPort() != -1){
        			String ipLIP = peer.getIP() + ":" + peer.getListeningPort();
            		this.peerIPListeningPortList.put(ipLIP, peer);
        		}
        	}
        	
        	return exist;
		}
    	
    }

    /**
     * Add the download task to the list of active (i.e. Handshake is ok) tasks
     * @param id String
     * @param dt DownloadTask
     */
    public synchronized void addActiveTask(String id, DownloadTask dt) {
    	
        synchronized (this.task) {
        	
            if (!this.task.containsKey(id)) {
                //logger.info("addActiveTask... (" + id + " / " + dt.getIdTask() + ")");
                this.task.put(id, dt);
                
            } else {
            	logger.warn("addActiveTask... (" + id + " / " + dt.getIdTask() + ") already exists");
            	dt.end();
            }
        }
    }

    /**
     * Called when a new peer connects to the client. Check if it is already
     * registered in the peer list, and if not, create a new DownloadTask for it
     * @param s Socket
     */
    public synchronized void connectionAccepted(Socket s) {
    	
        synchronized (this.task) {
        	
            String idPeerConnection = s.getInetAddress().getHostAddress() + ":" + s.getPort();
            
            if (!this.task.containsKey(idPeerConnection)) {
            	
            	DownloadTask dt = new DownloadTask(this.peerList.get(idPeerConnection), this.torrent.info_hash_as_binary, this.clientID, true, this.getBitField(), s);
            	dt.setDownloadManager(this);
            	dt.addDTListener(this);
            	
            	if(!this.peerList.containsKey(idPeerConnection)){
            		this.peerList.put(dt.peer.toString(), dt.peer);
            	}
            	
            	if(dt.peer.getIDTracker() != null){
            		logger.info("connectionAccepted... (" + idPeerConnection + " / " + dt.peer.getIDTracker() + ")");
            	}else{
            		logger.info("connectionAccepted... (" + idPeerConnection + " )");
            	}
            	
                dt.start();
                
            } else if(this.task.containsKey(idPeerConnection)){
            	try {
					s.close();
				} catch (IOException e) {
					logger.error("connection not accepted ... close socket: " + e);
					e.printStackTrace();
				}
            	logger.warn(s.getInetAddress().getHostAddress() + ":" + s.getPort() + " connection not Accepted Exist? ... " + this.task.get(idPeerConnection).getIdTask());
           
            }
            
        }
    }

    /**
     * Compute the bitfield byte array from the isComplete BitSet
     * @return byte[]
     */
    public byte[] getBitField() {
        int l = (int) Math.ceil((double)this.nbPieces / 8.0);
        byte[] bitfield = new byte[l];
        for (int i = 0; i < this.nbPieces; i++)
            if (this.isComplete.get(i)) {
                bitfield[i / 8] |= 1 << (7 - i % 8);
            }
        return bitfield;
    }

    public float getCompleted() {
        try {
            return (float) (((float) (100.0)) * ((float)
                                                 (this.isComplete.cardinality())) /
                            ((float) (this.nbPieces)));
        } catch (Exception e) {
            return 0.00f;
        }
    }

    public float getDLRate() {
        try {
            float rate = 0.00f;
            List<Peer> l = new LinkedList<Peer>(this.peerList.values());

            for (Iterator<Peer> it = l.iterator(); it.hasNext(); ) {
                Peer p = it.next();
                if (p.getDLRate(false) > 0)
                    rate = /*rate +*/ p.getDLRate(false);

            }
            return rate / (1024 * 10);
        } catch (Exception e) {
            return 0.00f;
        }
    }

    public float getULRate() {
        try {
            float rate = 0.00f;
            List<Peer> l = new LinkedList<Peer>(this.peerList.values());

            for (Iterator<Peer> it = l.iterator(); it.hasNext(); ) {
                Peer p = it.next();
                if (p.getULRate(false) > 0)
                    rate = rate + p.getULRate(true);

            }
            return rate / (1024 * 10);
        } catch (Exception e) {
            return 0.00f;
        }
    }

	public TorrentFile getTorrentFile(){
		return this.torrent;
	}

	public DownloadManagerListener getDownloadManagerListener() {
		return downloadManagerListener;
	}

	public void setDownloadManagerListener(DownloadManagerListener downloadManagerListener) {
		this.downloadManagerListener = downloadManagerListener;
	}

	public long getLastTimeSendPieceBlock() {
		return lastTimeSendPieceBlock;
	}

	public void setLastTimeSendPieceBlock(long lastTimeSendPieceBlock) {
		this.lastTimeSendPieceBlock = lastTimeSendPieceBlock;
	}
	
	public long getInitTimeStartedProtocol() {
		return initTimeStartedProtocol;
	}

	public void setInitTimeStartedProtocol(long initTimeStartedProtocol) {
		this.initTimeStartedProtocol = initTimeStartedProtocol;
	}

	public byte[] getClientID(){
		return this.clientID;
	}

	public long getLastTimeReceivePieceBlock() {
		return lastTimeReceivePieceBlock;
	}

	public void setLastTimeReceivePieceBlock(long lastTimeReceivePieceBlock) {
		this.lastTimeReceivePieceBlock = lastTimeReceivePieceBlock;
	}

	public int getIntervalUpdateListPeers() {
		return intervalUpdateListPeers;
	}

	public void setIntervalUpdateListPeers(int intervalUpdateListPeers) {
		this.intervalUpdateListPeers = intervalUpdateListPeers;
	}

	public float getThresholdEndGameTest() {
		return thresholdEndGameTest;
	}

	public void setThresholdEndGameTest(float threshold) {
		this.thresholdEndGameTest = threshold;
	}
	
}

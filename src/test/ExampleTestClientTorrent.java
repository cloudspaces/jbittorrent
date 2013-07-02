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

import java.util.Map;

import jbittorrent.DownloadManager;
import jbittorrent.DownloadManagerListener;
import jbittorrent.TorrentFile;
import jbittorrent.TorrentProcessor;
import jbittorrent.Utils;

import org.apache.log4j.Logger;

/**
 * 
 * Simple example to show how you can download files using bittorrent protocol 
 * starting with a given .torrent file.
 * 
 * @author Sandra Ferrer Celma
 * @version 0.1
 * 
 */
public class ExampleTestClientTorrent implements DownloadManagerListener {

	private DownloadManager downloadManager;
	private boolean isComplete;
	private boolean isSeed;
	
	private long maxTimeWaitUntilFirstPieceDownloaded = 10 * 60 * 1000; 
	private long maxTimeWaitUntilFirstPieceUploaded = 10 * 60 * 1000; 
	private long maxTimeWaitWithoutUpload = 30 * 60 * 1000; 
	private long maxTimeWaitWithoutDownload = 30 * 60 * 1000; 
	
	private static Logger logger = Logger.getLogger(ExampleTestClientTorrent.class);
	
	
	/**
	 * 
	 * @param t {@link TorrentFile}
	 * @param pathTempFile String path where to save the downloaded file.
	 * @param intervalUpdateListPeers float establishes the periodicity, in seconds, 
	 * with which the peer is announced in the tracker; in accordance 
	 * to the tracker protocol HTTP/HTTPS and the announceURL specified in the TorrentFile.  
	 * The connections of the peers with which it interacts, will be revised, and 
	 * new connections will be established, if the peers list returned by the tracker, 
	 * contains new peers.
	 * @param blockSize int determines the size, in KB, of the blocs.  By default is 16 kB.
	 * @param thresholdEndGameTest float determines when to activate EndGameStrategy.  
	 * If it is -1 (by default value) will be activated according to the protocol 
	 * defined by Bram Cohen (when all the pieces have been solicited). Otherwise, 
	 * a percentage of total download of the file (i.e. 90%) is determined like 
	 * the moment where will be activated the EndGameStrategy.
	 * @param saveInformationTransfer boolean will save or not the information of 
	 * he pieces sent or received of the peers.  This information will be shown by logs.
	 * @throws Exception Error while processing torrent file. Please restart the client
	 */
	public ExampleTestClientTorrent(TorrentFile t, String pathTempFile, int intervalUpdateListPeers, int blockSize, float thresholdEndGameTest, boolean saveInformationTransfer) throws Exception{
		
		this.downloadManager = null;
		this.isComplete = false;
		
		if (t != null) {
			this.downloadManager = new DownloadManager(t, Utils.generateID(), pathTempFile, intervalUpdateListPeers, blockSize, thresholdEndGameTest);
			this.downloadManager.setSaveInformationPieceTransfer(saveInformationTransfer);
			if(this.downloadManager==null) {
                throw new Exception("Provided file is not a valid torrent file");
            }
            else{
                this.downloadManager.setDownloadManagerListener(this);
            }
			
			this.isComplete = this.downloadManager.isComplete();
			this.isSeed = this.isComplete;
			
		}else {
            throw new Exception("Error while processing torrent file. Please restart the client");
        }
	}
	
	/**
	 * 
	 * @param torrentData byte[] 
	 * @param pathTempFile String path where will save the download file.
	 * @param blockSize int determines the size (in kB.) of the blocks. (By default is 16 kB.)
	 * @throws Exception
	 */
	public ExampleTestClientTorrent(byte[] torrentData, String pathTempFile, int blockSize) throws Exception{
		
		this.downloadManager = null;
		this.isComplete = false;
				
		TorrentProcessor tp = new TorrentProcessor();
		TorrentFile torrentFile = tp.getTorrentFile(tp.parseTorrent(torrentData));
		
		if (torrentFile != null) {
			this.downloadManager = new DownloadManager(torrentFile, Utils.generateID(), pathTempFile, blockSize);
			if(this.downloadManager==null) {
                throw new Exception("Provided file is not a valid torrent file");
            }
            else{
                this.downloadManager.setDownloadManagerListener(this);
            }
			
			this.isComplete = this.downloadManager.isComplete();
			this.isSeed = this.isComplete;
			
		}else {
            throw new Exception("Error while processing torrent file. Please restart the client");
        }
	}
	
	public byte[] getClientID(){
		
		byte[] id = null;
		
		if(this.downloadManager!=null)
			id = this.downloadManager.getClientID();
		
		return id;
	}
	
	/**
	 * Starts the bittorrent protocol to download or to share a file, in accordance with a .torrent file.
	 * @throws Exception
	 */
	public void runClientTorrent() throws Exception{
		
		if(this.downloadManager != null && !this.downloadManager.isRunning()){
			
			if(this.isComplete){
				logger.info("Run SEED clientTorrent ID: "+Utils.byteArrayToByteString(this.getClientID()));
			}else{
				logger.info("Run SIMPLE clientTorrent ID: "+Utils.byteArrayToByteString(this.getClientID()));
			}
			
			this.downloadManager.startListening(6881, 6889);
			this.downloadManager.startTrackerUpdate();
			this.downloadManager.start();
			
		}else
			throw new Exception("Error already running");
		
			
	}
	
	
	public void stopClientTorrent(){
		
		if(this.downloadManager != null && this.downloadManager.isRunning()){
			
			logger.info("Stop DownloadManager ... ");
			this.downloadManager.stopBlockUntilCompletion();
			
			logger.info("Stop Update Tracker ... ");
			this.downloadManager.stopTrackerUpdate();
			
			this.downloadManager.displayLogs();
			
			logger.info("Stop and Clear Active Tasks ... ");
			this.downloadManager.stopAndClearActiveTask();
			
			logger.info("Close Temp Files ... ");
			this.downloadManager.closeTempFiles();
			
			
		}
			
	}

	/**
	 * If it is a client and not is a seed, after to end the download, will be stopped the process.
	 */
	public void downloadComplete() {
		this.isComplete = true;
		
		try {
			Thread.sleep(20 * 1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		this.stopClientTorrent();
	}
	

	/**
	 * If it is a seed, it will stop the process if all the peers of the swarm 
	 * are seed or if after it has been elapsed maximum time (in minutes) 
	 * without to have received any request of any peer.  
	 * If it’s not a seed, it will stop the process when there aren’t more 
	 * peers in the swarm or if after it has been elapsed maximum time (in minutes)
	 * without to have received pieces of other peers. 
	 */
	public void updatePeerListState(Map<String, Integer> mapState, long lastTimeSendPieceBlock, long lastTimeReceivePieceBlock, long initTimeStartedProtocol) {
		
		int nComplete = mapState.get("complete").intValue();
		int nIncomplete = mapState.get("incomplete").intValue();
		boolean stop = false;
		
		if(initTimeStartedProtocol != -1){
			
			//SIMPLE CLIENT
			if(!this.isComplete && lastTimeReceivePieceBlock == -1
					&& (System.currentTimeMillis() - initTimeStartedProtocol) > this.maxTimeWaitUntilFirstPieceDownloaded){
				stop = true;
			
			}if(!this.isComplete && lastTimeReceivePieceBlock != -1 && (nComplete+nIncomplete == 1 || (nComplete+nIncomplete > 1 && nComplete == 0))){
				stop = true;
				
			
			}else if(!this.isComplete && lastTimeReceivePieceBlock != -1 && nComplete+nIncomplete > 1 
					&& (System.currentTimeMillis() - lastTimeReceivePieceBlock) > this.maxTimeWaitWithoutDownload){
				stop = true;
				
			//SEED CLIENT
			}else if(this.isComplete && lastTimeSendPieceBlock == -1 && lastTimeSendPieceBlock == -1 
					&& (System.currentTimeMillis() - initTimeStartedProtocol) > this.maxTimeWaitUntilFirstPieceUploaded){
				stop = true;
				
			}else if(this.isComplete && (lastTimeSendPieceBlock != -1 || !this.isSeed) && (nComplete+nIncomplete == 1 || (nComplete+nIncomplete > 1 && nIncomplete == 0))){
				stop = true;
				
			}else if(this.isComplete && lastTimeSendPieceBlock != -1 && nComplete+nIncomplete > 1 
					&& (System.currentTimeMillis() - lastTimeSendPieceBlock) > this.maxTimeWaitWithoutUpload ){
				stop = true;
				
			}else if(this.isComplete && lastTimeSendPieceBlock == -1 && lastTimeSendPieceBlock != -1 
					&& (nComplete+nIncomplete == 1 || (nComplete+nIncomplete > 1 && nIncomplete == 0))){
				stop = true;
			}
			
			if(stop){
				this.stopClientTorrent();
			}
		}
		
		logger.info("Complete = "+mapState.get("complete"));
		logger.info("Downloaded = "+mapState.get("downloaded"));
		logger.info("Incomplete = "+mapState.get("incomplete"));
		
	}
	
	

	/**
	 * <torrentPath> .../jbittorrent/example/client1/funvideo.torrent
	 * <savePath> .../jbittorrent/example/client1/
	 * <blockSize> 16
	 * optional:
	 * <intervalUpdateListPeers> 170
	 * <thresholdEndGameTest> -1
	 * 
	 * .../jbittorrent/example/client1/funvideo.torrent .../jbittorrent/example/client1/ 16 60 90
	 * 
	 * @param args
	 */
	
	public static void main(String[] args) {
        try {
            TorrentProcessor tp = new TorrentProcessor();
            
            if(args.length < 3){
                System.err.println("Wrong parameter number\r\n\r\nUse:\r\n" +
                        "ExampleTestClientTorrent <torrentPath> <savePath> <blockSize> " +
                        "optional <intervalUpdateListPeers> <thresholdEndGameTest>");
                System.exit(1);
            }
            TorrentFile t = tp.getTorrentFile(tp.parseTorrent(args[0]));
            
            String savePath = args[1];
            int blockSize = new Integer(args[2]);
            
            int intervalUpdateListPeers = 60;
            float thresholdEndGameTest = 90.0f;
            
            if(args.length > 3){
            	intervalUpdateListPeers = new Integer(args[3]);
            }
            
            if(args.length > 4){
            	thresholdEndGameTest = new Float(args[4]);
            }
            
            if (t != null) {
            	
            	ExampleTestClientTorrent eCT = new ExampleTestClientTorrent(t, savePath, intervalUpdateListPeers, blockSize, thresholdEndGameTest, true);
            	eCT.runClientTorrent();
            	
            } else {
            	logger.error("Provided file is not a valid torrent file");
                System.exit(1);
            }
        } catch (Exception e) {
            logger.error("Error while processing torrent file. Please restart the client: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }
	
	
}

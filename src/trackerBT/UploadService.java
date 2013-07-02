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

package trackerBT;

import java.io.*;
import java.util.*;

import org.jdom.*;
import org.jdom.filter.*;
import org.jdom.input.*;
import org.jdom.output.*;
import org.jdom.output.Format;
import simple.http.*;
import simple.http.load.*;
import simple.http.serve.*;
import simple.http.upload.*;

/**
 * Service called to upload a torrent file to the tracker
 *
 * <p>Titre : TrackerBT</p>
 *
 * <p>Description : Bittorrent Tracker</p>
 *
 * <p>Copyright : Copyright (c) 2007</p>
 *
 * @author Baptiste Dubuis
 * @version 1.0
 */
public class UploadService extends Service {
    Document torrents;
    /**
     * Default constructor needed by Simple server
     * @param context The context of the server
     */
    public UploadService(Context context) {
        super(context);
    }

    /**
     * Process the client upload request and returns the corresponding answer. In case of
     * successful upload, the torrent will be registered on the tracker and freely available
     * for download
     * @param req The request received from the client. It must be a multipart data-form type request
     * @param resp HTML file representing the result of the request
     * @throws IOException
     */
    public void process(Request req, Response resp) throws IOException {
        Map multipart = null;
        try {
            multipart = this.mapParameters(this.processRequest(req));
        } catch (IllegalArgumentException iae) {
            OutputStream out = resp.getOutputStream();
            out.write(this.generateResponse(3));
            out.flush();
            out.close();
            return;
        }
        byte[] info = null;

        FileItem fi = (FileItem) multipart.get("torrent");
        Map torrent = BDecoder.decode(new BufferedInputStream(fi.getInputStream()));
        info = BEncoder.encode((Map) torrent.get("info"));

        String name = (String) multipart.get("name");
        String infoFile = (String) multipart.get("info");
        String comment = (String) multipart.get("comment");
        String hash = Utils.byteArrayToByteString(Utils.hash(
                info));
        int res = this.registerTorrent(name, infoFile, comment, hash);
        if (res == 0) {
            String torDir = (String) Constants.get("torrentsDir");
            new File(torDir).mkdirs();
            OutputStream os = new FileOutputStream(torDir + hash + ".torrent");
            os.write(fi.get());
        }

        resp.set("Content-Type", "text/html");
        resp.setDate("Date", System.currentTimeMillis());
        resp.set("Server", (String) Constants.get("servername"));
        OutputStream out = resp.getOutputStream();
        out.write(this.generateResponse(res));
        out.flush();
        out.close();
    }

    /**
     * Generate the html file bytes representing the answer. Answer depends on the
     * response status
     * @param id Represent the response status. 0 means upload was successful.
     * Otherwise id represent the kind of error that occured and a corresponding
     * answer is created.
     * @return byte[] The html response bytes
     */
    private byte[] generateResponse(int id) {
        byte[] response = null;
        switch (id) {
        case 0:
            System.out.println("Torrent successfully added to database");
            return ("<html><head><title>File upload successful</title></head>" +
                    "<body><h1>Congratulations</h1><h3>Your file was successfully" +
                    " added to our database").getBytes();
        case 1:
            System.err.println("Torrent already in database...");
            return ("<html><head><title>File already registered</title></head>" +
                    "<body><h1>File already in our database</h1><h3>There is " +
                    "already a file with the same hash in our database").
                    getBytes();

        case 2:
            System.err.println("This file is not XML valid");
            return ("<html><head><title>Internal Error</title></head>" +
                    "<body><h1>Sorry!</h1><h3>An error occured during the process" +
                    " of your file. Try again later").getBytes();

        case 3:
            System.err.println("Missing parameters name or file");
            return ("<html><head><title>Missing parameters!</title></head>" +
                    "<body><h1>Error!</h1><h3>Your file was not successfully" +
                    " added to our database because your request was uncomplete." +
                    "\r\nCheck it again and try sending your file again...").
                    getBytes();

        case 4:
            System.err.println("File doesn't match the required DTD");
            return ("<html><head><title>Internal Error</title></head>" +
                    "<body><h1>Sorry!</h1><h3>An error occured during the process" +
                    " of your file. Try again later").getBytes();

        default:
            return ("<html><head><title>Internal Error</title></head>" +
                    "<body><h1>Sorry!</h1><h3>An error occured during the process" +
                    " of your file. Try again later").getBytes();
        }
    }

    /**
     * Process the request and retrieves the parameters and file contained in it.
     * @param req The received request
     * @return List List of file items representing the parameters uploaded
     */
    private List processRequest(final Request req) {
        if (req != null) {
            if (FileUpload.isMultipartContent(req)) {
                try {
                    DiskFileUpload dfu = new DiskFileUpload();
                    return dfu.parseRequest(req);
                } catch (FileUploadException fue) {

                }
            }
            return null;
        }
        throw new IllegalArgumentException(
                "UploadService.processRequest: null Request argument");
    }

    /**
     * Creates a Map representing the parameters listed in l
     * @param l List The list of parameters
     * @return Map
     */
    private Map mapParameters(final List l) {
        Map param = new HashMap();
        if (l != null) {
            try {
                for (Iterator it = l.iterator(); it.hasNext(); ) {
                    FileItem fi = (FileItem) it.next();
                    if (fi.isFormField()) {
                        param.put(fi.getFieldName(), fi.getString());
                    } else if ((fi.getSize() > 0) &&
                               (fi.getName() != null) &&
                               (fi.getName().trim().length() > 0)) {
                        param.put("torrent", fi);
                    }
                }
                return param;
            } catch (Exception e) {

            }
            return null;
        }
        throw new IllegalArgumentException(
                "UploadService.fileWithList: null items.");
    }

    /**
     * Look if the torrent is already registered on this tracker
     * @param root Root element of the XML document representing the torrent database
     * @param hash Id of the file being uploaded
     * @return boolean true if the file doesn't exist on the tracker, false otherwise
     */
    private boolean isNewTorrent(Element root, String hash) {
        for (Iterator hashIt = root.getDescendants(new ElementFilter("hash"));
                               hashIt.hasNext(); ) {
            if (((Element) hashIt.next()).getText().matches(hash)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Add a torrent to the torrent database according to the given parameters
     * @param root Root element of the XML document representing the torrents database
     * @param name Name of the torrent
     * @param infoFile Name of an optional info file (.nfo, .txt, ...)
     * @param comment Comment about the torrent
     * @param hash Torrent id. It is the hash of the 'info' dictionary in the torrent
     */
    private void addTorrent(Element root, String name, String infoFile,
                            String comment, String hash) {

        Element newTorrent = new Element("torrent");

        Element nameTag = new Element("name");
        nameTag.setText(name);
        newTorrent.addContent(nameTag);

        Element infoTag = new Element("info");
        infoTag.setText(infoFile);
        newTorrent.addContent(infoTag);

        Element commentTag = new Element("comment");
        commentTag.setText(comment);
        newTorrent.addContent(commentTag);

        Element hashTag = new Element("hash");
        hashTag.setText(hash);
        newTorrent.addContent(hashTag);

        root.addContent(newTorrent);
    }

    /**
     * Create new XML document for registering torrents
     * @param xmlTorrent The file the document will be saved in
     * @throws IOException
     */
    private void createEmptyTrackerXML(File xmlTorrent) throws IOException {
        System.out.println("No torrent currently in DB, creating new DB");
        xmlTorrent.getParentFile().mkdirs();
        FileWriter fw = new FileWriter(xmlTorrent);
        fw.write(
                "<?xml version=\"1.0\"?>\r\n<torrentlist>\r\n</torrentlist>");
        fw.flush();
        fw.close();
    }

    /**
     * Try to register the torrent given with the parameters
     * @param name Name of the torrent
     * @param infoFile Potential info file name
     * @param comment Comment about the torrent
     * @param hash Torrent id
     * @return int Integer representing an error type. 0 means no error occured
     */
    public int registerTorrent(String name, String infoFile, String comment,
                               String hash) {

        if (name == null || hash == null) {
            return 3;
        }

        if (!name.endsWith(".torrent")) {
            name += ".torrent";
        }

        SAXBuilder sb = new SAXBuilder();
        XMLOutputter xmlout = new XMLOutputter(Format.getPrettyFormat());

        try {
            File xmlTorrent = new File((String) Constants.get("torrentXML"));

            if (!xmlTorrent.exists() || xmlTorrent.length() == 0) {
                this.createEmptyTrackerXML(xmlTorrent);
            }
            try {
                this.torrents = sb.build(xmlTorrent);
            } catch (JDOMException je) {
                return 2;
            }

            Element root = this.torrents.getRootElement();

            if (root == null || !root.getName().matches("torrentlist")) {
                return 4;
            }

            if (!this.isNewTorrent(root, hash)) {
                return 1;
            }

            this.addTorrent(root, name, infoFile, comment, hash);

            FileOutputStream fos = new FileOutputStream(xmlTorrent);
            xmlout.output(this.torrents, fos);
            fos.close();
        } catch (IOException ioe) {

        }
        return 0;
    }
}

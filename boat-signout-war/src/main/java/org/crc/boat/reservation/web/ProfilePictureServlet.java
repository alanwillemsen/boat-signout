package org.crc.boat.reservation.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.crc.boat.reservation.dao.ProfilePictureDao;
import org.crc.boat.reservation.model.ProfilePicture;

import com.cilogi.shiro.gae.GaeUser;
import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.shiro.web.BaseServlet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;

@Singleton
@SuppressWarnings("serial")
public class ProfilePictureServlet extends BaseServlet {
    static final Logger LOG = Logger.getLogger(ProfilePictureServlet.class.getName());
    
    private ProfilePictureDao profilePictureDao;

    ObjectMapper mapper = new ObjectMapper();
    
    @Inject
    public ProfilePictureServlet(ProfilePictureDao profilePictureDao, Provider<GaeUserDAO> daoProvider) {
        super(daoProvider);
        this.profilePictureDao = profilePictureDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String id = StringUtils.substringAfterLast(req.getRequestURI(), "/");
        if(StringUtils.isBlank(id)){
            return;
        }
        ProfilePicture pic = profilePictureDao.getProfilePictureById(Long.valueOf(id));
        if (pic == null) {
            resp.sendError(HTTP_STATUS_NOT_FOUND);
            return;
        }
        IOUtils.write(pic.getImage(), resp.getOutputStream());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ServletFileUpload upload = new ServletFileUpload();
        FileItemIterator iter;
        try {
            iter = upload.getItemIterator(req);
            if(iter.hasNext()){
                FileItemStream imageItem = iter.next();
                InputStream imgStream = imageItem.openStream();
                byte[] byteArray = IOUtils.toByteArray(imgStream);
                if(byteArray == null || byteArray.length == 0){
                    resp.sendRedirect("/profile/");
                    return;
                }
                // No current user (not logged in, or suspended/deleted mid-session): don't
                // NPE -- just bounce to logout so the client re-authenticates.
                GaeUser currentUser = getCurrentGaeUser();
                if (currentUser == null) {
                    resp.sendRedirect("/logout");
                    return;
                }

                ImagesService imagesService = ImagesServiceFactory.getImagesService();
                Image oldImage = ImagesServiceFactory.makeImage(byteArray);
                Transform resize = ImagesServiceFactory.makeResize(400, 400, 0.5d, 0.5d);
                Image newImage = imagesService.applyTransform(resize, oldImage);

                ProfilePicture profilePic = new ProfilePicture(currentUser.getName(), newImage.getImageData());
                profilePic = profilePictureDao.saveProfilePicture(profilePic);

                currentUser.setPictureUrl("picture/" + profilePic.getId());
                daoProvider.get().saveUser(currentUser, false);
            }
            resp.sendRedirect("/profile/");
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }



}

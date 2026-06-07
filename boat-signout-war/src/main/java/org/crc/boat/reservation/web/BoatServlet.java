package org.crc.boat.reservation.web;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.crc.boat.reservation.dao.BoatDao;
import org.crc.boat.reservation.model.Boat;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Singleton
@SuppressWarnings("serial")
public class BoatServlet extends HttpServlet {
    static final Logger LOG = Logger.getLogger(BoatServlet.class.getName());

    private BoatDao boatDao;

    @Inject
    BoatServlet(BoatDao boatDAO) {
        this.boatDao = boatDAO;
    }

    ObjectMapper mapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        list(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws JsonParseException, JsonMappingException, IOException {
        String action = StringUtils.substringAfterLast(req.getRequestURI(), "/");
        // Creating/updating/deleting boats is admin-only. (GET /boats stays open: the
        // reservation flows need the boat list for every member.)
        if (("post".equals(action) || "jsonlist".equals(action) || "delete".equals(action)) && !isAdmin()) {
            mapper.writeValue(resp.getOutputStream(), new JsonErrorResponse("Only admins can manage boats"));
            return;
        }
        switch (action) {
        case "post":
            creatUpdate(req, resp);
            break;

        case "jsonlist":
            jsonlist(req, resp);
            break;

        case "delete":
            delete(req, resp);
            break;

        default:
            list(req, resp);
            break;
        }

    }

    private boolean isAdmin() {
        return SecurityUtils.getSubject().hasRole("admin");
    }

    protected void list(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonResponse result = new JsonResponse(boatDao.listBoats());
        mapper.writeValue(resp.getOutputStream(), result);

    }

    protected void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boatDao.deleteBoat(new Boat(req.getParameter("name")));
        mapper.writeValue(resp.getOutputStream(),  new JsonResponse(null));
    }

    protected void creatUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter("name");
        if(StringUtils.isBlank(name)){
            JsonErrorResponse result = new JsonErrorResponse("Name can not be blank");
            mapper.writeValue(resp.getOutputStream(), result);
            return;
        }
        Boat boat = new Boat(name);

        String displayName = req.getParameter("displayName");
        if(displayName != null && !displayName.trim().isEmpty()) {
            boat.setDisplayName(displayName);
        }

        String rowable = req.getParameter("rowable");
        if(rowable == null || "false".equalsIgnoreCase(rowable)){
            boat.setRowable(false);
        }else{
            boat.setRowable(true);
        }
        String event = req.getParameter("event");
        if(event == null || "false".equalsIgnoreCase(event)){
            boat.setEvent(false);
        }else{
            boat.setEvent(true);
        }
        boat.setWarningMessage(req.getParameter("warningMessage"));
        Boat savedBoat = boatDao.saveBoat(boat);
        JsonResponse result = new JsonResponse(savedBoat);
        mapper.writeValue(resp.getOutputStream(), result);
    }
    
    protected void jsonlist(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Boat> boats = mapper.readValue(req.getParameter("boats"), new TypeReference<List<Boat>>(){});
        for (Boat boat : boats) {
            boatDao.saveBoat(boat);
        }
    }

}

package org.crc.boat.reservation.web;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.crc.boat.reservation.dao.BoatDao;
import org.crc.boat.reservation.dao.ReservationDao;
import org.crc.boat.reservation.model.Boat;
import org.crc.boat.reservation.model.Reservation;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.cilogi.shiro.gae.GaeUser;
import com.cilogi.shiro.gae.GaeUserDAO;
import com.cilogi.shiro.web.BaseServlet;
import com.fasterxml.jackson.databind.ObjectMapper;

@Singleton
@SuppressWarnings("serial")
public class ReservationServlet extends BaseServlet  {


    static final Logger LOG = Logger.getLogger(ReservationServlet.class.getName());

    
    private ReservationDao reservationDao;
    private BoatDao boatDao;
    ObjectMapper mapper = new ObjectMapper();
    final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd h:mm a").withZone(DateTimeZone.forID("America/Toronto"));
    
    private enum PARAMETER {
        boat,
        reserveDate,
        reserveTime,
        duration,
        units,
        allowConflicts,
        from,
        to
    };
    
    private static final Map<String, Long> UNITS_TO_SECONDS_MAP = new HashMap<String, Long>(){{
        put("Minutes", 1000L * 60L);
        put("Hours", 1000L * 60L * 60L);
        put("Days", 1000L * 60L * 60L * 24L);
    }};
    
    private static final Map<PARAMETER, String> REQUIRED_PARAMETERS = new HashMap<PARAMETER, String>(){{
        put(PARAMETER.boat, "Please select a boat.");
        put(PARAMETER.reserveDate, "Please select a date.");
        put(PARAMETER.reserveTime, "Please select a time.");
        put(PARAMETER.duration, "Please select a duration.");
        put(PARAMETER.units, "Please specify the duration unit");
    }};
    
    @Inject
    ReservationServlet(ReservationDao reservationDAO, Provider<GaeUserDAO> daoProvider,
                       BoatDao boatDao) {
        super(daoProvider);
        this.reservationDao = reservationDAO;
        this.boatDao = boatDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        GaeUser currentGaeUser = getCurrentGaeUser();
        if(currentGaeUser == null){
            LOG.info("User not logged in.");
            resp.sendRedirect("/logout");
            return;
        }

        List<String> errors = new ArrayList<>();
        String from = getAndValidate(PARAMETER.from, req, errors);
        String to = getAndValidate(PARAMETER.to, req, errors);
        if(!errors.isEmpty()){
            mapper.writeValue(resp.getOutputStream(), new JsonErrorResponse(StringUtils.join(errors, "<br>")));
            return;
        }
        
        List<Reservation> reservations = reservationDao.listReservations(Long.valueOf(from), Long.valueOf(to));
        for (Reservation reservation : reservations) {
            reservation.setCanDelete(currentGaeUser.getName().equals(reservation.getUserName()));
        }
        mapper.writeValue(resp.getOutputStream(), new CalendarResponse(reservations));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // "Reserve Multiple" (availability grid) posts a JSON batch to /reserve/batch; the single
        // "Reserve a Boat" form posts form fields to /reserve. Both share buildReservation()/
        // checkConflicts() so the conflict/timezone rules are identical.
        if (req.getRequestURI().endsWith("/batch")) {
            doBatch(req, resp);
            return;
        }

        List<String> errors = new ArrayList<>();
        String boat = getAndValidate(PARAMETER.boat, req, errors);
        String reserveDate = getAndValidate(PARAMETER.reserveDate, req, errors);
        String reserveTime = getAndValidate(PARAMETER.reserveTime, req, errors);
        String durationString = getAndValidate(PARAMETER.duration, req, errors);
        String units = getAndValidate(PARAMETER.units, req, errors);
        if(!errors.isEmpty()){
            mapper.writeValue(resp.getOutputStream(), new JsonErrorResponse(StringUtils.join(errors, "<br>")));
            return;
        }
        String allowConflicts = req.getParameter(PARAMETER.allowConflicts.toString());

        GaeUser user = getCurrentGaeUser();
        if (user == null) {
            issueJson(resp, HTTP_STATUS_FORBIDDEN, MESSAGE, "Session expired");
            return;
        }

        Reservation reservation;
        try {
            reservation = buildReservation(boat, reserveDate, reserveTime, durationString, units, user);
        } catch (ReservationException e) {
            mapper.writeValue(resp.getOutputStream(), new JsonErrorResponse(e.getMessage()));
            return;
        }

        if(!"true".equals(allowConflicts)){
            JsonErrorResponse conflict = checkConflicts(reservation);
            if (conflict != null) {
                mapper.writeValue(resp.getOutputStream(), conflict);
                return;
            }
        }

        reservationDao.saveReservation(reservation);
        mapper.writeValue(resp.getOutputStream(), new JsonResponse(null));
    }

    /**
     * Create many reservations in one request (from the "Reserve Multiple" grid). Best-effort:
     * each item is checked and saved in turn (so a later item's conflict check sees earlier saves),
     * and the response reports which ones were created and which failed. Reuses the exact same
     * build/conflict logic as the single flow.
     */
    private void doBatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        GaeUser user = getCurrentGaeUser();
        if (user == null) {
            issueJson(resp, HTTP_STATUS_FORBIDDEN, MESSAGE, "Session expired");
            return;
        }
        BatchRequest request;
        try {
            request = mapper.readValue(req.getInputStream(), BatchRequest.class);
        } catch (Exception e) {
            mapper.writeValue(resp.getOutputStream(), new JsonErrorResponse("Could not read reservations."));
            return;
        }
        if (request == null || request.items == null || request.items.isEmpty()) {
            mapper.writeValue(resp.getOutputStream(), new JsonErrorResponse("No reservations selected."));
            return;
        }

        BatchResponse result = new BatchResponse();
        for (int i = 0; i < request.items.size(); i++) {
            BatchItem item = request.items.get(i);
            Reservation reservation;
            try {
                reservation = buildReservation(item.boat, item.reserveDate, item.reserveTime,
                        item.duration, item.units, user);
            } catch (ReservationException e) {
                result.failed.add(new BatchFailure(i, item.boat, item.reserveTime, "INVALID", e.getMessage()));
                continue;
            }
            if (!request.allowConflicts) {
                JsonErrorResponse conflict = checkConflicts(reservation);
                if (conflict != null) {
                    result.failed.add(new BatchFailure(i, item.boat, item.reserveTime,
                            conflict.ErrorType, conflict.Message));
                    continue;
                }
            }
            reservationDao.saveReservation(reservation);
            result.created++;
        }
        mapper.writeValue(resp.getOutputStream(), result);
    }

    /** Parse + validate the reservation fields and build a Reservation. Same rules (and error
     *  messages) as the single form flow; America/Toronto parsing via dateTimeFormatter. */
    private Reservation buildReservation(String boat, String reserveDate, String reserveTime,
            String durationString, String units, GaeUser user) throws ReservationException {
        if (StringUtils.isBlank(boat)) throw new ReservationException(REQUIRED_PARAMETERS.get(PARAMETER.boat));
        if (StringUtils.isBlank(reserveDate)) throw new ReservationException(REQUIRED_PARAMETERS.get(PARAMETER.reserveDate));
        if (StringUtils.isBlank(reserveTime)) throw new ReservationException(REQUIRED_PARAMETERS.get(PARAMETER.reserveTime));
        if (StringUtils.isBlank(durationString)) throw new ReservationException(REQUIRED_PARAMETERS.get(PARAMETER.duration));
        if (StringUtils.isBlank(units)) throw new ReservationException(REQUIRED_PARAMETERS.get(PARAMETER.units));

        DateTime startDate;
        try {
            startDate = dateTimeFormatter.parseDateTime(reserveDate + " " + reserveTime.toUpperCase());
        } catch (Exception e) {
            throw new ReservationException("Date and time is not valid.");
        }
        long duration;
        try {
            duration = Long.valueOf(durationString);
        } catch (NumberFormatException e) {
            throw new ReservationException("Duration is not a valid number.");
        }
        Long unitInSeconds = UNITS_TO_SECONDS_MAP.get(units.trim());
        if (unitInSeconds == null) {
            throw new ReservationException("Duration unit is not recognized: " + units);
        }
        long start = startDate.getMillis();
        long end = start + duration * unitInSeconds;

        Reservation reservation = new Reservation();
        reservation.setBoatName(boat);
        String boatDisplayName = boatDao.getBoat(boat)
                .map(Boat::getDisplayName)
                .orElse(boat);
        reservation.setBoatDisplayName(boatDisplayName);
        reservation.setUserName(user.getName());
        String displayName = user.getDisplayName();
        if (StringUtils.isBlank(displayName)) {
            displayName = user.getName().replaceAll("@.*", "");
        }
        reservation.setUserDisplayName(displayName);
        reservation.setTitle(MessageFormat.format("{0} - {1}", reservation.getBoatName(), reservation.getUserDisplayName()));
        reservation.setStart(start);
        reservation.setEnd(end);
        reservation.setPictureUrl(StringUtils.isBlank(user.getPictureUrl()) ? "img/rower.png" : user.getPictureUrl());
        return reservation;
    }

    /** River-safety + same-boat conflict checks. Returns a JsonErrorResponse (with ErrorType
     *  RIVER_NOT_ROWABLE or CONFLICT) to report, or null if the reservation is clear. */
    private JsonErrorResponse checkConflicts(Reservation reservation) {
        if (!reservationDao.riverIsRowable(reservation)) {
            String message = "The river is not safe for rowing. " +
                    "<b>Do not row club boats until flow rate is below 100 m<sup>3</sup>/s</b>";
            JsonErrorResponse error = new JsonErrorResponse(message);
            error.ErrorType = "RIVER_NOT_ROWABLE";
            return error;
        }
        List<Reservation> conflicts = reservationDao.hasConflict(reservation);
        if (conflicts != null && !conflicts.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (Reservation r : conflicts) {
                messages.add(MessageFormat.format("{0} to {1} {2}",
                        dateTimeFormatter.print(r.getStart()).toLowerCase(),
                        dateTimeFormatter.print(r.getEnd()).toLowerCase(),
                        r.getUserDisplayName()));
            }
            JsonErrorResponse error = new JsonErrorResponse(StringUtils.join(messages, "<br>"));
            error.ErrorType = "CONFLICT";
            return error;
        }
        return null;
    }

    private static class ReservationException extends Exception {
        ReservationException(String message) { super(message); }
    }

    // ---- Batch request/response shapes (JSON via Jackson) ----
    public static class BatchRequest {
        public boolean allowConflicts;
        public List<BatchItem> items;
    }

    public static class BatchItem {
        public String boat;
        public String reserveDate;   // yyyy-MM-dd
        public String reserveTime;   // h:mm a
        public String duration;      // numeric string
        public String units;         // Minutes | Hours | Days
    }

    public static class BatchResponse {
        public String Result = "OK";
        public int created = 0;
        public List<BatchFailure> failed = new ArrayList<>();
    }

    public static class BatchFailure {
        public int index;
        public String boat;
        public String reserveTime;
        public String errorType;
        public String message;
        public BatchFailure(int index, String boat, String reserveTime, String errorType, String message) {
            this.index = index;
            this.boat = boat;
            this.reserveTime = reserveTime;
            this.errorType = errorType;
            this.message = message;
        }
    }

    private String getAndValidate(PARAMETER parameter, HttpServletRequest req, List<String> errors){
         String value = req.getParameter(parameter.toString());
         if(StringUtils.isBlank(value)){
             errors.add(REQUIRED_PARAMETERS.get(parameter));
         }
         return value;
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String id = StringUtils.substringAfterLast(req.getRequestURI(), "/");
        if(!StringUtils.isBlank(id)){
            reservationDao.deleteReservation(Long.parseLong(id.trim()));
        }
        mapper.writeValue(resp.getOutputStream(), new JsonResponse(null));
    }
    
    
    
    
    
}

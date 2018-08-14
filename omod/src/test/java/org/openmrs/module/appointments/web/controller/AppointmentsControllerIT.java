package org.openmrs.module.appointments.web.controller;

import org.codehaus.jackson.type.TypeReference;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.dao.AppointmentAuditDao;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointments.model.AppointmentAudit;
import org.openmrs.module.appointments.service.AppointmentsService;
import org.openmrs.module.appointments.util.DateUtil;
import org.openmrs.module.appointments.web.BaseIntegrationTest;
import org.openmrs.module.appointments.web.contract.AppointmentCount;
import org.openmrs.module.appointments.web.contract.AppointmentDefaultResponse;
import org.openmrs.module.appointments.web.contract.AppointmentsSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppointmentsControllerIT extends BaseIntegrationTest {

    private final String BASE_URL = "/rest/v1/appointments";

    @Autowired
    AppointmentsService appointmentsService;

    @Autowired
    AppointmentAuditDao appointmentAuditDao;

    @Before
    public void setUp() throws Exception {
        executeDataSet("appointmentTestData.xml");
        Context.getAdministrationService().setGlobalProperty("disableDefaultAppointmentValidations", "false");
    }

    @Test
    public void should_GetAllAppointments() throws Exception {
        List<AppointmentDefaultResponse> responses = deserialize(handle(newGetRequest(BASE_URL)),
                new TypeReference<List<AppointmentDefaultResponse>>() {
                });
        assertEquals(7, responses.size());
    }

    @Test
    public void should_GetAllAppointmentsForDate() throws Exception {
        List<AppointmentDefaultResponse> responses = deserialize(handle(newGetRequest(BASE_URL,
                new Parameter("forDate", "2108-08-15T00:00:00.0Z"))),
                new TypeReference<List<AppointmentDefaultResponse>>() {
                });
        assertEquals(4, responses.size());
    }

    @Test
    public void shouldThrowErrorWhenAppointmentDoesNotHavePatientInIT() throws Exception {
        String content = "{ \"providerUuid\": \"823fdcd7-3f10-11e4-adec-0800271c1b75\", " +
                "\"appointmentNumber\": \"1\",  " +
                "\"startDateTime\": \"2017-07-20\", " +
                "\"serviceUuid\": \"c36006d4-9fbb-4f20-866b-0ece245615c1\", " +
                "\"endDateTime\": \"2017-07-20\",  " +
                "\"appointmentKind\": \"WalkIn\"}";

        MockHttpServletResponse response = handle(newPostRequest("/rest/v1/appointments/new", content));
        assertEquals(400, response.getStatus());
    }

    @Test
    public void shouldThrowErrorWhenAppointmentDoesNotHaveServiceInIT() throws Exception {
        String content = "{ \"providerUuid\": \"823fdcd7-3f10-11e4-adec-0800271c1b75\", " +
                "\"appointmentNumber\": \"1\",  " +
                "\"startDateTime\": \"2017-07-20\", " +
                "\"patientUuid\": \"2c33920f-7aa6-48d6-998a-60412d8ff7d5\", " +
                "\"endDateTime\": \"2017-07-20\",  " +
                "\"appointmentKind\": \"WalkIn\"}";

        MockHttpServletResponse response = handle(newPostRequest("/rest/v1/appointments/new", content));
        assertEquals(400, response.getStatus());
    }

    @Test
    public void shouldCreateAuditEventsWhenDetailsChangesOnEditAppointment() throws Exception {
        String content = "{ \"uuid\": \"c36006e5-9fbb-4f20-866b-0ece245615a7\", " +
                "\"appointmentNumber\": \"1\",  " +
                "\"patientUuid\": \"2c33920f-7aa6-48d6-998a-60412d8ff7d5\", " +
                "\"serviceUuid\": \"c36006d4-9fbb-4f20-866b-0ece245615c1\", " +
                "\"serviceTypeUuid\": \"672546e5-9fbb-4f20-866b-0ece24564578\", " +
                "\"startDateTime\": \"2017-07-20\", " +
                "\"endDateTime\": \"2017-07-20\",  " +
                "\"comments\": \"Some notes\",  " +
                "\"appointmentKind\": \"WalkIn\"}";

        MockHttpServletResponse response = handle(newPostRequest("/rest/v1/appointments/new", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        Appointment appointment = appointmentsService.getAppointmentByUuid("c36006e5-9fbb-4f20-866b-0ece245615a7");
        List<AppointmentAudit> historyForAppointment = appointmentAuditDao
                .getAppointmentHistoryForAppointment(appointment);
        assertEquals(1, historyForAppointment.size());
        assertNotNull(historyForAppointment.get(0).getDateCreated());
        assertNotNull(historyForAppointment.get(0).getCreator());
        assertEquals(appointment, historyForAppointment.get(0).getAppointment());
        assertNotNull(historyForAppointment.get(0).getNotes());
    }


    @Test
    public void shouldGetASpecificAppointment() throws Exception {
        AppointmentDefaultResponse response = deserialize(
                handle(newGetRequest(BASE_URL + "/75504r42-3ca8-11e3-bf2b-0800271c13346")),
                new TypeReference<AppointmentDefaultResponse>() {
                });
        assertEquals("GAN200000", response.getPatient().get("identifier"));
    }

    @Test
    public void should_changeAppointmentStatusWithDate() throws Exception {
        String onDate = "2108-08-22T10:30:00.0Z";
        String content = "{ \"toStatus\": \"CheckedIn\", \"onDate\":\"" + onDate + "\"}";
        MockHttpServletResponse response = handle(newPostRequest(BASE_URL + "/c36006e5-9fbb-4f20-866b-0ece245615a7/changeStatus", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        Appointment appointmentByUuid = appointmentsService.getAppointmentByUuid("c36006e5-9fbb-4f20-866b-0ece245615a7");
        assertNotNull(appointmentByUuid);

        List<AppointmentAudit> historyForAppointment = appointmentAuditDao.getAppointmentHistoryForAppointment(appointmentByUuid);
        assertEquals(1, historyForAppointment.size());
        assertNotNull(historyForAppointment.get(0).getId());
        assertNotNull(historyForAppointment.get(0).getNotes());
        assertEquals(DateUtil.convertToLocalDateFromUTC(onDate).toInstant().toString(), historyForAppointment.get(0).getNotes());
    }

    @Test
    public void should_changeAppointmentStatusWithoutDate() throws Exception {
        String content = "{ \"toStatus\": \"CheckedIn\"}";
        MockHttpServletResponse response = handle(newPostRequest(BASE_URL + "/c36006e5-9fbb-4f20-866b-0ece245615a7/changeStatus", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        Appointment appointmentByUuid = appointmentsService.getAppointmentByUuid("c36006e5-9fbb-4f20-866b-0ece245615a7");
        assertNotNull(appointmentByUuid);
        List<AppointmentAudit> historyForAppointment = appointmentAuditDao
                .getAppointmentHistoryForAppointment(appointmentByUuid);
        assertEquals(1, historyForAppointment.size());
        assertNotNull(historyForAppointment.get(0).getDateCreated());
        assertNotNull(historyForAppointment.get(0).getCreator());
        assertEquals(appointmentByUuid, historyForAppointment.get(0).getAppointment());
        assertEquals(appointmentByUuid.getStatus(), historyForAppointment.get(0).getStatus());
        assertNull(historyForAppointment.get(0).getNotes());
    }

    @Test
    public void should_throwExceptionForInvalidStatusChange() throws Exception {
        String content = "{ \"toStatus\": \"Scheduled\"}";
        MockHttpServletResponse response = handle(newPostRequest(BASE_URL + "/c36006e5-9fbb-4f20-866b-0ece245615a7/changeStatus", content));
        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void should_throwExceptionForInvalidAppointment() throws Exception {
        String content = "{ \"toStatus\": \"Scheduled\"}";
        MockHttpServletResponse response = handle(newPostRequest(BASE_URL + "/c36006e5-9fbb-4f20-866b-0ece245615a8/changeStatus", content));
        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void shouldThrowExceptionForInvalidAppointmentOnUndoStatus() throws Exception {
        MockHttpServletResponse response = handle(newPostRequest(BASE_URL + "/randomUuid/undoStatusChange", "{}"));
        assertNotNull(response);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("Appointment does not exist"));
    }

    @Test
    public void shouldUndoCheckedInAppointment() throws Exception {
        String content = "{ \"providerUuid\": \"823fdcd7-3f10-11e4-adec-0800271c1b75\", " +
                "\"appointmentNumber\": \"3\",  " +
                "\"patientUuid\": \"2c33920f-7aa6-48d6-998a-60412d8ff7d5\", " +
                "\"serviceUuid\": \"c36006d4-9fbb-4f20-866b-0ece245615c1\", " +
                "\"startDateTime\": \"2017-07-20\", " +
                "\"endDateTime\": \"2017-07-20\",  " +
                "\"appointmentKind\": \"WalkIn\"}";

        MockHttpServletResponse response = handle(newPostRequest("/rest/v1/appointments/new", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        AppointmentDefaultResponse appointmentDefaultResponse = deserialize(response, new TypeReference<AppointmentDefaultResponse>() {
        });
        String appointmentUuid = appointmentDefaultResponse.getUuid();

        content = "{ \"toStatus\": \"CheckedIn\"}";
        response = handle(newPostRequest(BASE_URL + "/" + appointmentUuid + "/changeStatus", content));
        assertEquals(200, response.getStatus());
        content = "{}";
        response = handle(newPostRequest(BASE_URL + "/" + appointmentUuid + "/undoStatusChange", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        Appointment appointment = appointmentsService.getAppointmentByUuid(appointmentUuid);
        assertNotNull(appointment);
        assertEquals("Scheduled", appointment.getStatus().toString());
    }

    @Test
    public void shouldUndoMissedAppointment() throws Exception {
        String appointmentUuid = "c36006e5-9fbb-4f20-866b-0ece245615a7";
        String content = "{ \"toStatus\": \"CheckedIn\"}";
        MockHttpServletResponse response = handle(newPostRequest(BASE_URL + "/" + appointmentUuid + "/changeStatus", content));
        assertEquals(200, response.getStatus());
        content = "{ \"toStatus\": \"Missed\"}";
        response = handle(newPostRequest(BASE_URL + "/" + appointmentUuid + "/changeStatus", content));
        assertEquals(200, response.getStatus());
        content = "{}";
        response = handle(newPostRequest(BASE_URL + "/" + appointmentUuid + "/undoStatusChange", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        Appointment appointment = appointmentsService.getAppointmentByUuid(appointmentUuid);
        assertNotNull(appointment);
        assertEquals("CheckedIn", appointment.getStatus().toString());
    }

    @Test
    public void shouldThrowExceptionWhenThereIsNoPriorActionToUndo() throws Exception {
        String content = "{ \"providerUuid\": \"823fdcd7-3f10-11e4-adec-0800271c1b75\", " +
                "\"appointmentNumber\": \"3\",  " +
                "\"patientUuid\": \"2c33920f-7aa6-48d6-998a-60412d8ff7d5\", " +
                "\"serviceUuid\": \"c36006d4-9fbb-4f20-866b-0ece245615c1\", " +
                "\"startDateTime\": \"2017-07-20\", " +
                "\"endDateTime\": \"2017-07-20\",  " +
                "\"appointmentKind\": \"WalkIn\"}";
        MockHttpServletResponse response = handle(newPostRequest("/rest/v1/appointments/new", content));
        assertNotNull(response);
        assertEquals(200, response.getStatus());
        AppointmentDefaultResponse appointmentDefaultResponse = deserialize(response, new TypeReference<AppointmentDefaultResponse>() {
        });
        String appointmentUuid = appointmentDefaultResponse.getUuid();
        content = "{}";
        response = handle(newPostRequest(BASE_URL + "/" + appointmentUuid + "/undoStatusChange", content));
        assertNotNull(response);
        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("No status change actions to undo"));
    }

    @Test
    public void shouldGetCountOfAppointmentsForAllServicesInAGivenDateRange() throws Exception {
        MockHttpServletRequest getRequest = newGetRequest(BASE_URL + "/summary", new Parameter("startDate", "2108-08-15T00:00:00.0Z"),
                new Parameter("endDate", "2108-08-22T00:00:00.0Z"));
        List<AppointmentsSummary> appointmentsSummaries
                = deserialize(handle(getRequest), new TypeReference<List<AppointmentsSummary>>() {
        });
        AppointmentsSummary appointmentsSummary = appointmentsSummaries.get(0);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date appointmentDate = simpleDateFormat.parse("2108-08-15");
        assertEquals(2, appointmentsSummaries.size());
        assertNotNull(appointmentsSummary);
        assertEquals(1, appointmentsSummary.getAppointmentService().getAppointmentServiceId(), 0);
        assertEquals("c36006e5-9fbb-4f20-866b-0ece245615a6", appointmentsSummary.getAppointmentService().getUuid());
        assertEquals(1, appointmentsSummary.getAppointmentCountMap().size());
        Map<String, AppointmentCount> appointmentCountMap = appointmentsSummary.getAppointmentCountMap();
        Map appointmentCount = (Map) appointmentCountMap.get("2108-08-15");
        assertNotNull(appointmentCount);
        assertEquals(4, appointmentCount.get("allAppointmentsCount"));
        assertEquals(1, appointmentCount.get("missedAppointmentsCount"));
        assertEquals(appointmentDate.getTime(), appointmentCount.get("appointmentDate"));
        assertEquals("c36006e5-9fbb-4f20-866b-0ece245615a6", appointmentCount.get("appointmentServiceUuid"));
    }

}

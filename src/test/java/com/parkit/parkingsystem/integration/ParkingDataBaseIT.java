package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static final DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    public static void setUp() {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
        dataBasePrepareService.clearDataBaseEntries();
    }

    @BeforeEach
    public void setUpPerTest() throws Exception {
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1);
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("b");

        dataBasePrepareService = new DataBasePrepareService();
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    public static void tearDown() {
        dataBasePrepareService.clearDataBaseEntries();
    }

    @Test
    public void testParkingACar() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();

        Ticket newTicket = ticketDAO.getTicket("b");
        assertEquals("b", newTicket.getVehicleRegNumber());

        ParkingSpot parkingSpotOfTheNewTicket = newTicket.getParkingSpot();
        assertFalse(parkingSpotOfTheNewTicket.isAvailable());
    }

    @Test
    public void testParkingLotExit() throws InterruptedException {
        testParkingACar();

        Thread.sleep(1000);

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processExitingVehicle();

        Ticket updatedTicket = ticketDAO.getTicket("b");

        assertNotNull(updatedTicket);
        assertNotNull(updatedTicket.getOutTime());
        assertEquals(1, parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR));
    }

    @Test
    public void testParkingLotExitRecurringUser() {
        Ticket initTicket = new Ticket();
        initTicket.setVehicleRegNumber("b");
        initTicket.setInTime(new Date(System.currentTimeMillis() - (25 * 60 * 60 * 1000)));
        initTicket.setOutTime(new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000)));
        initTicket.setPrice(1.5);
        initTicket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
        ticketDAO.saveTicket(initTicket);

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        Ticket secondTicket = new Ticket();
        secondTicket.setVehicleRegNumber("b");
        secondTicket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        secondTicket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, false));
        ticketDAO.saveTicket(secondTicket);

        parkingService.processExitingVehicle();
        Ticket secondTicketFinal = ticketDAO.getTicket("b");

        assertEquals(2, ticketDAO.getNbTicket("b"));
        assertEquals(1.425, secondTicketFinal.getPrice(), 0.01);
    }
}
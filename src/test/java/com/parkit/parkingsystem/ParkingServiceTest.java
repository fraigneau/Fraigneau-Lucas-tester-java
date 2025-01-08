package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private static ParkingService parkingService;

    @Mock
    private static InputReaderUtil inputReaderUtil;
    @Mock
    private static ParkingSpotDAO parkingSpotDAO;
    @Mock
    private static TicketDAO ticketDAO;

    @BeforeEach
    public void setUpPerTest() {
        try {
            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to set up test mock objects");
        }
    }

    private Ticket setupTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(3);
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, false));
        ticket.setVehicleRegNumber("ABC");
        ticket.setPrice(0);
        ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        ticket.setOutTime(null);

        return ticket;
    }

    @Test
    public void testProcessIncomingVehicle() throws Exception {

        Ticket ticket = setupTicket();

        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABC");
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(2);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(ticketDAO.getNbTicket("ABC")).thenReturn(2);
        when(ticketDAO.getTicket(anyString())).thenReturn(ticket);

        // WHEN
        parkingService.processIncomingVehicle();

        // THEN
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        verify(ticketDAO, Mockito.times(1)).saveTicket(any(Ticket.class));
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABC");

        Ticket savedTicket = ticketDAO.getTicket("ABC");

        assertEquals(3, savedTicket.getId());
        assertEquals("ABC", savedTicket.getVehicleRegNumber());
        assertEquals(System.currentTimeMillis() - (60 * 60 * 1000), savedTicket.getInTime().getTime(), 1000);
        assertNull(savedTicket.getOutTime());
        assertEquals(0, savedTicket.getPrice());
        assertFalse(savedTicket.getParkingSpot().isAvailable());
    }

    @Test
    public void testProcessExitingVehicle() throws Exception {

        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
        Ticket ticket = setupTicket();

        ticket.setParkingSpot(parkingSpot);

        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABC");
        when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
        when(ticketDAO.getNbTicket(anyString())).thenReturn(2);
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

        // when
        parkingService.processExitingVehicle();

        // then
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABC");
        verify(ticketDAO, Mockito.times(1)).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));

        Ticket updatedTicket = ticketDAO.getTicket("ABC");
        assertEquals(3, updatedTicket.getId());
        assertEquals("ABC", updatedTicket.getVehicleRegNumber());
        assertEquals(System.currentTimeMillis() - (60 * 60 * 1000), updatedTicket.getInTime().getTime(), 1000);
        assertEquals(System.currentTimeMillis(), updatedTicket.getOutTime().getTime(), 1000);
        assertTrue(updatedTicket.getPrice() > 0);
    }

    @Test
    public void testProcessExitingVehicleUnableUpdate() throws Exception {

        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
        Ticket ticket = setupTicket();
        ticket.setParkingSpot(parkingSpot);

        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABC");
        when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
        when(ticketDAO.getNbTicket(anyString())).thenReturn(2);
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false);

        // when
        parkingService.processExitingVehicle();

        // then
        verify(ticketDAO, Mockito.times(1)).getNbTicket("ABC");
        verify(ticketDAO, Mockito.times(1)).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO, Mockito.times(0)).updateParking(any(ParkingSpot.class));

        Ticket updatedTicket = ticketDAO.getTicket("ABC");
        assertEquals(parkingSpot, updatedTicket.getParkingSpot());
    }

    @Test
    public void testGetNextParkingNumberIfAvailable() throws Exception {

        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);

        // when
        ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

        // then
        assertEquals(1, parkingSpot.getId());
        assertEquals(ParkingType.CAR, parkingSpot.getParkingType());
        assertTrue(parkingSpot.isAvailable());

    }

    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() {

        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(any(ParkingType.class))).thenReturn(-1);

        Exception exception = assertThrows(Exception.class, () -> parkingService.getNextParkingNumberIfAvailable());
        assertEquals("Error fetching parking number from DB. Parking slots might be full", exception.getMessage());

    }

    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() {

        when(inputReaderUtil.readSelection()).thenReturn(3);

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> parkingService.getNextParkingNumberIfAvailable());
        assertEquals("Entered input is invalid", exception.getMessage());

    }
}
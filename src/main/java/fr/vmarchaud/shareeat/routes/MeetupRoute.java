package fr.vmarchaud.shareeat.routes;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.gson.JsonObject;
import com.stripe.exception.APIConnectionException;
import com.stripe.exception.APIException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;

import fr.vmarchaud.shareeat.Core;
import fr.vmarchaud.shareeat.enums.EnumState;
import fr.vmarchaud.shareeat.objects.Invitation;
import fr.vmarchaud.shareeat.objects.Location;
import fr.vmarchaud.shareeat.objects.Meetup;
import fr.vmarchaud.shareeat.objects.User;
import fr.vmarchaud.shareeat.request.MeetupCreateRequest;
import fr.vmarchaud.shareeat.services.DataService;
import fr.vmarchaud.shareeat.services.LocationService;
import fr.vmarchaud.shareeat.services.MeetupService;
import fr.vmarchaud.shareeat.services.StripeService;
import fr.vmarchaud.shareeat.services.UserService;
import fr.vmarchaud.shareeat.utils.Utils;

@Path("/meetup")
@RolesAllowed("USER")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MeetupRoute {

	private UserService		userSrv = Core.getInstance().getUserService();
	private MeetupService	meetupSrv = Core.getInstance().getMeetupService();
	private LocationService	locationSrv = Core.getInstance().getLocationService();
	private StripeService	stripeSrv = Core.getInstance().getStripeService();
	private DataService		dataSrv = Core.getInstance().getDataService();
	
	
	@Path("create")
	@POST
	public Response createMeetup(MeetupCreateRequest request, @Context ContainerRequestContext context) {
		if (request == null || !request.isValid())
			return Response.status(Status.BAD_REQUEST).build();
		
		// Get user from the context
		User user = (User)context.getProperty("user");
		
		// Get if the location actually exist in our database
		Location loc = locationSrv.byId(request.getLocation());
		if (loc == null)
			return Response.status(Status.NOT_FOUND).build();
		
		// Try to parse the string to date to verify if its a valid date
		try {
			Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(request.getDate());
		} catch (ParseException e) {
			e.printStackTrace();
			return Response.status(Status.BAD_REQUEST).build();
		}
		
		// Ask to create the service and from the result, return created or not.
		Meetup meetup = meetupSrv.createMeetup(request.getName(), user, loc, request.getTags(), request.getInvited(), request.getDate());
		if (meetup != null)
			return Response.ok(meetup.getId()).build();
		else
			return Response.ok(Status.INTERNAL_SERVER_ERROR).build();
	}
	
	@Path("payement")
	@POST
	public Response	pay(JsonObject request, @Context ContainerRequestContext context) {
		if (!request.has("id") || !request.has("token") || !Utils.isUUID(request.get("id").getAsString()))
			return Response.status(Status.BAD_REQUEST).build();
		
		// Get all data we need
		User user = (User)context.getProperty("user");
		Meetup meetup = meetupSrv.byId(request.get("id").getAsString());
		String token = request.get("token").getAsString();
		boolean plusone = false;
		
		// Verify that the user is trying to pay for a meetup that he will participe in.
		if (!meetup.getUsers().stream().anyMatch(invit -> invit.getReceiver().getId().compareTo(user.getId()) == 0)) {
			return Response.status(Status.BAD_REQUEST).build();
		}
		
		// The plus one if present
		if (request.has("plusone") && Utils.isUUID(request.get("plusone").getAsString())) {
			UUID id = UUID.fromString(request.get("plusone").getAsString());
			plusone = meetup.getUsers().stream().anyMatch(invit -> invit.getReceiver().getId().compareTo(id) == 0);
		}
		
		boolean state = false;
		try {
			state = stripeSrv.chargeUser(meetup, user, token, plusone);
		} catch (AuthenticationException | InvalidRequestException | APIConnectionException | CardException
				| APIException e) {

			return Response.status(Status.NOT_ACCEPTABLE).entity(e.getMessage()).build();
		}
		if (state) {
			// Valid the invitation if payement is valid
			Invitation invitation = meetup.getUsers().stream().filter(invit -> invit.getReceiver().getId().compareTo(user.getId()) == 0).findFirst().orElse(null);
			meetupSrv.updateInvitation(invitation, EnumState.ACCEPTED);
			
			// And valid the plusone invit if it exist
			if (plusone) {
				UUID id = UUID.fromString(request.get("plusone").getAsString());
				invitation = meetup.getUsers().stream().filter(invit -> invit.getReceiver().getId().compareTo(id) == 0).findFirst().orElse(null);
				meetupSrv.updateInvitation(invitation, EnumState.ACCEPTED);
			}
			return Response.ok().build();
		}
		else
			return Response.status(Status.NOT_ACCEPTABLE).build();
	}
	
	
	@Path("show/{id}")
	@GET
	public Response show(@PathParam("id") String id, @Context ContainerRequestContext context) {
		if (!Utils.isUUID(id))
			return Response.status(Status.BAD_REQUEST).build();
		Meetup meetup = meetupSrv.byId(id);
		if (meetup == null)
			return Response.status(Status.NOT_FOUND).build();
		return Response.ok(meetup).build();
	}
	
	@Path("refuse/{id}")
	@GET
	public Response refuse(@PathParam("id") String id, @Context ContainerRequestContext context) {
		if (!Utils.isUUID(id))
			return Response.status(Status.BAD_REQUEST).build();
		
		// Get the meetup by id
		Meetup meetup = meetupSrv.byId(id);
		if (meetup == null)
			return Response.status(Status.NOT_FOUND).build();
		// Get user from context
		User user = (User)context.getProperty("user");
		
		// If the user is not invited in the meetup
		if (!meetup.getUsers().stream().anyMatch(invit -> invit.getReceiver().getId().compareTo(user.getId()) == 0))
			return Response.status(Status.FORBIDDEN).build();

		// Set refused the invitation
		Invitation invitation = meetup.getUsers().stream().filter(invit -> invit.getReceiver().getId().compareTo(user.getId()) == 0).findFirst().orElse(null);
		invitation.setState(EnumState.REFUSED);
		return Response.ok().build();
	}
}

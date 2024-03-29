package fr.vmarchaud.shareeat.services;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.stripe.Stripe;
import com.stripe.exception.APIConnectionException;
import com.stripe.exception.APIException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Charge;

import fr.vmarchaud.shareeat.Core;
import fr.vmarchaud.shareeat.enums.EnumEnv;
import fr.vmarchaud.shareeat.objects.Meetup;
import fr.vmarchaud.shareeat.objects.Payement;
import fr.vmarchaud.shareeat.objects.User;
import fr.vmarchaud.shareeat.utils.CustomConfig;

public class StripeService {

	private DataService		dataSrv = Core.getInstance().getDataService();
	
	public StripeService() {
		Stripe.apiKey = "sk_test_3hlxc9p0CgECOsh5f5ez0z7I";
	}
	
	/**
	 * Create a charge for the user
	 * @param meetup : The meetup that user pay for
	 * @param user : The user that will pay
	 * @param token : Stripe payement token used to create the charge
	 * @return
	 * 
	 * @throws APIException 
	 * @throws CardException 
	 * @throws APIConnectionException 
	 * @throws InvalidRequestException 
	 * @throws AuthenticationException 
	 */
	public boolean chargeUser(Meetup meetup, User user, String token, boolean plusone) throws AuthenticationException, InvalidRequestException, APIConnectionException, CardException, APIException {
		
		// Create the charge with price and token
		Map<String, Object> chargeParams = new HashMap<String, Object>();
		chargeParams.put("amount", (plusone ? 2 : 1) * 100);
		chargeParams.put("currency", "EUR");
		chargeParams.put("source", token);
		chargeParams.put("capture", true);
		chargeParams.put("receipt_email", user.getMail());
		chargeParams.put("description", "ShareEat diner (" + meetup.getId().toString() + ")");
		
		// Try to create the carge
		Charge charge = null;
		charge = Charge.create(chargeParams);
		
		Payement payement =	new Payement(UUID.randomUUID(), user, CustomConfig.ENV == EnumEnv.PROD, token, charge.getId(), meetup);
		dataSrv.getPayements().add(payement);
		try {
			dataSrv.payementsDao.createOrUpdate(payement);
		} catch (SQLException e) {
			e.printStackTrace();
			return true;
		}
		return true;
	}
}

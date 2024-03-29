package fr.vmarchaud.shareeat.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class UserLoginRequest {
	public String		username;
	public String		password;
	public String		accessToken;
	public LoginMethod 	method;
	
	public boolean isValid() {
		if (username == null || username.length() == 0 || password == null || password.length() == 0)
			return false;
		else
			return true;
	}
	
	public enum LoginMethod {
		STANDALONE;
	}
}

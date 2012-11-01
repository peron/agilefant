package fi.hut.soberit.agilefant.security;

import fi.hut.soberit.agilefant.business.UserBusiness;
import org.springframework.dao.DataAccessException;
import org.springframework.security.Authentication;
import org.springframework.security.AuthenticationException;
import org.springframework.security.GrantedAuthoritiesContainer;
import org.springframework.security.GrantedAuthority;
import org.springframework.security.providers.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.userdetails.User;
import org.springframework.security.userdetails.UserDetails;
import org.springframework.security.userdetails.UsernameNotFoundException;
import org.springframework.util.Assert;


public class PreAuthenticatedAgilefantUserDetailsService implements AuthenticationUserDetailsService {


    private UserBusiness userBusiness;

    public void setUserBusiness(UserBusiness userBusiness) {
        this.userBusiness = userBusiness;
    }

    /**
     * API method to provide UserDetails-object for given username. Returns
     * AgilefantUserDetails - instances.
     */
    public UserDetails loadUserByUsername(String userName)
            throws UsernameNotFoundException, DataAccessException {


        // try getting user by given username
        fi.hut.soberit.agilefant.model.User user = userBusiness.retrieveByLoginName(userName);

        // no user found, throw exception
        if (user == null)
            throw new UsernameNotFoundException("no such user: " + userName);

        // success, return UserDetails-instance
        return new AgilefantUserDetails(user);
    }

    /**
     * Get a UserDetails object based on the user name contained in the given
     * token, and the GrantedAuthorities as returned by the
     * GrantedAuthoritiesContainer implementation as returned by
     * the token.getDetails() method.
     */
    public final UserDetails loadUserDetails(Authentication token) throws AuthenticationException {
            return loadUserByUsername(token.getPrincipal().toString());
    }

    /**
     * Creates the final <tt>UserDetails</tt> object. Can be overridden to customize the contents.
     *
     * @param token       the authentication request token
     * @param authorities the pre-authenticated authorities.
     */
    protected UserDetails createuserDetails(Authentication token, GrantedAuthority[] authorities) {
        return new User(token.getName(), "N/A", true, true, true, true, authorities);
    }
}

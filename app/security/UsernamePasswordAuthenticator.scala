package security

import org.pac4j.core.context.{Pac4jConstants, WebContext}
import org.pac4j.core.credentials.UsernamePasswordCredentials
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.exception.CredentialsException
import org.pac4j.core.profile.CommonProfile
import org.pac4j.core.util.CommonHelper

/** Authenticator[UsernamePasswordCredentials] 是 base authentication 认证接口，实现这个接口并注册到 SecurityModule 的
  * Clients 中。
  *
  * Authenticator 不仅要提供用户名和口令的认证，同时需要提供授权角色（Role）。
  * */
class UsernamePasswordAuthenticator extends Authenticator[UsernamePasswordCredentials]{
    override def validate(credentials: UsernamePasswordCredentials, context: WebContext): Unit = {
        if (credentials == null) {
            throw new CredentialsException("No credential")
        }

        /** 认证 */
        val username = credentials.getUsername()
        val password = credentials.getPassword()
        if (CommonHelper.isBlank(username)) {
            throw new CredentialsException("Username cannot be blank")
        }
        if (CommonHelper.isBlank(password)) {
            throw new CredentialsException("Password cannot be blank")
        }
        if (CommonHelper.areNotEquals(username, password)) {
            throw new CredentialsException("Username : '" + username + "' does not match password")
        }

        val profile = new CommonProfile()
        profile.setId(username)
        profile.addAttribute(Pac4jConstants.USERNAME, username)

        /** 授权角色 */
        if (username=="jeff") profile.addRole("ROLE_ADMIN") else profile.addRole("ROLE_CUSTOMER")
        credentials.setUserProfile(profile)
    }
}

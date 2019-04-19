package security

import org.apache.commons.lang.StringUtils
import org.pac4j.core.authorization.authorizer.ProfileAuthorizer
import org.pac4j.core.context.WebContext
import org.pac4j.core.profile.CommonProfile

/**
  * ProfileAuthorizer 是认证器, 接受一个表达用户 Profile 格式的类型参数. 这个 Profile 要在 SecurityModule.configure 函数中注册
  * */
class CustomAuthorizer extends ProfileAuthorizer[CommonProfile] {
    def isAuthorized(context: WebContext, profiles: java.util.List[CommonProfile]): Boolean = {
        return isAnyAuthorized(context, profiles)
    }

    def isProfileAuthorized(context: WebContext, profile: CommonProfile): Boolean = {
        if (profile == null) {
            false
        } else {
            StringUtils.startsWith (profile.getUsername, "jle")
        }
    }
}

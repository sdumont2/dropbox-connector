{
  "authenticated": ${authenticated?string},
  "auth_url": "${auth_url!""}" <#if authenticated >,
  "display_name": "${display_name}",
  "quota": ${quota?string.computer},
  "quota_normal": ${quota_normal?string.computer},
  "email": "${email}" </#if>
}

{ "authenticated": ${authenticated?string}, "auth_url": "${auth_url!""}"<#if authenticated >,
"display_name": "${display_name}",
 "email": "${email}"
</#if> }

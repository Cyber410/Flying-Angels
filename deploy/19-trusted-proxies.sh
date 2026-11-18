#!/bin/sh
set -eu
set -f

# Trust is opt-in. In AWS, supply only the ALB subnet CIDRs and restrict the
# task's inbound port 80 to the ALB security group.
cidrs=$(printf '%s' "${NGINX_TRUSTED_PROXY_CIDRS:-}" | tr ',' ' ')
for cidr in $cidrs; do
    case "$cidr" in
        *[!0-9a-fA-F:./]*|*/0*)
            echo 'NGINX_TRUSTED_PROXY_CIDRS must contain explicit IP CIDRs, never an all-addresses range.' >&2
            exit 1 ;;
    esac
    case "$cidr" in
        */*) ;;
        *) echo 'NGINX_TRUSTED_PROXY_CIDRS requires CIDR notation.' >&2; exit 1 ;;
    esac
done

{
    printf '%s\n' '# Generated at startup; original socket peer controls header trust.'
    printf '%s\n' 'geo $realip_remote_addr $trusted_edge_proxy {' '    default 0;'
    for cidr in $cidrs; do printf '    %s 1;\n' "$cidr"; done
    printf '%s\n' '}'
    for cidr in $cidrs; do printf 'set_real_ip_from %s;\n' "$cidr"; done
    # A single ALB in append mode adds the actual client as the last address.
    # Do not walk farther back into a chain that the client could have supplied.
    printf '%s\n' 'real_ip_header X-Forwarded-For;' 'real_ip_recursive off;'
} > /etc/nginx/conf.d/00-trusted-proxies.conf

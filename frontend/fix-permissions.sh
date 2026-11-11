#!/bin/sh
# Ensure all static assets are readable and directories traversable by the nginx worker user
chmod -R a+rX /usr/share/nginx/html 2>/dev/null || true


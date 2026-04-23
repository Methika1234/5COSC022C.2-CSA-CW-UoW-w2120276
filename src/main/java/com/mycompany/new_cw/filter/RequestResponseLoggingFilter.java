package com.mycompany.new_cw.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Logger;

@Provider
public class RequestResponseLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RequestResponseLoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        LOG.info("[INCOMING] " + req.getMethod() + " " + req.getUriInfo().getRequestUri());
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) throws IOException {
        LOG.info("[OUTGOING] " + req.getMethod() + " "
                + req.getUriInfo().getRequestUri()
                + " -> " + res.getStatus());
    }
}

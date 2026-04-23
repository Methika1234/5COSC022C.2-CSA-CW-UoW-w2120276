package com.mycompany.new_cw.exception;

import com.mycompany.new_cw.model.ErrorMessage;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @Override
    public Response toResponse(Throwable throwable) {
        LOG.log(Level.SEVERE, "Intercepted unhandled exception: " + throwable.getMessage(), throwable);

        // Preserve the status code for JAX-RS built-in exceptions (e.g. NotFoundException)
        if (throwable instanceof WebApplicationException) {
            int code = ((WebApplicationException) throwable).getResponse().getStatus();
            return Response.status(code)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorMessage(throwable.getMessage(), code))
                    .build();
        }

        // Everything else becomes a generic 500 — never expose internal details
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorMessage(
                        "An unexpected server-side error has occurred. The incident has been logged for investigation.",
                        500))
                .build();
    }
}

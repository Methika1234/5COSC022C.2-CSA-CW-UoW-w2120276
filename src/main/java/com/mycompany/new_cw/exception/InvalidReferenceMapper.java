package com.mycompany.new_cw.exception;

import com.mycompany.new_cw.model.ErrorMessage;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class InvalidReferenceMapper implements ExceptionMapper<LinkedResourceNotFoundException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(LinkedResourceNotFoundException ex) {
        ErrorMessage error = new ErrorMessage(ex.getMessage(), UNPROCESSABLE_ENTITY);
        return Response.status(UNPROCESSABLE_ENTITY)
                .type(MediaType.APPLICATION_JSON)
                .entity(error)
                .build();
    }
}

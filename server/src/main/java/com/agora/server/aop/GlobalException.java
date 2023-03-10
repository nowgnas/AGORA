package com.agora.server.aop;

import com.agora.server.auth.exception.TokenValidFailedException;
import com.agora.server.common.dto.ResponseDTO;
import com.agora.server.file.exception.FileTypeException;
import com.agora.server.file.exception.FileWriteException;
import com.agora.server.file.exception.GCSFileException;
import com.agora.server.openvidu.exception.AgoraOpenViduException;
import com.agora.server.user.exception.AlreadyExistUserException;
import com.agora.server.user.exception.DuplicateNickNameException;
import com.agora.server.user.exception.NoUserException;
import io.openvidu.java.client.OpenViduHttpException;
import io.openvidu.java.client.OpenViduJavaClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalException extends ResponseEntityExceptionHandler {
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ResponseDTO> NullPointerException(NullPointerException nullPointerException) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(nullPointerException.getMessage());
        res.setStatusCode(HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ResponseDTO> RuntimeException(RuntimeException r) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(r.getMessage());
        return new ResponseEntity<>(res, HttpStatus.REQUEST_TIMEOUT);
    }

    @ExceptionHandler(AlreadyExistUserException.class)
    public ResponseEntity<ResponseDTO> AlreadyExistUserException(AlreadyExistUserException a) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(a.getMessage());
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ExceptionHandler(DuplicateNickNameException.class)
    public ResponseEntity<ResponseDTO> DuplicateNickNameException(DuplicateNickNameException d) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(d.getMessage());
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ExceptionHandler(NoUserException.class)
    public ResponseEntity<ResponseDTO> NoUserException(NoUserException nu) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(nu.getMessage());
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ExceptionHandler(TokenValidFailedException.class)
    public ResponseEntity<ResponseDTO> TokenValidFailedException(TokenValidFailedException jwt) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(jwt.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ExceptionHandler(OpenViduJavaClientException.class)
    public ResponseEntity<ResponseDTO> OpenViduJavaClientException(OpenViduJavaClientException ov) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(ov.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);

    }

    @ExceptionHandler(OpenViduHttpException.class)
    public ResponseEntity<ResponseDTO> OpenViduHttpException(OpenViduHttpException ov) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(ov.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);

    }

    @ExceptionHandler(AgoraOpenViduException.class)
    public ResponseEntity<ResponseDTO> AgoraOpenViduException(AgoraOpenViduException ov) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(ov.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);

    }

    @ExceptionHandler(FileTypeException.class)
    public ResponseEntity<ResponseDTO> AgoraOpenViduException(FileTypeException e) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(e.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);

    }

    @ExceptionHandler(FileWriteException.class)
    public ResponseEntity<ResponseDTO> FileWriteException(FileTypeException e) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(e.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);

    }

    @ExceptionHandler(GCSFileException.class)
    public ResponseEntity<ResponseDTO> FileWriteException(GCSFileException e) {
        ResponseDTO res = new ResponseDTO();
        res.setMessage(e.getMessage());
        res.setState(false);
        return new ResponseEntity<>(res, HttpStatus.OK);

    }


}

package com.agora.server.file.util;

import com.agora.server.file.dto.FileDto;
import com.agora.server.file.exception.FileTypeException;
import com.agora.server.file.exception.GCSFileException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Component
public class DataBucketUtil {

    @Value("${spring.gcp.config.file}")
    private String gcpConfigFile;

    @Value("${spring.gcp.project.id}")
    private String gcpProjectId;

    @Value("${spring.gcp.bucket.id}")
    private String gcpBucketId;

    @Value("${spring.gcp.dir.name}")
    private String gcpDirectoryName;

    public FileDto uploadFile(MultipartFile multipartFile, String originalName, String contentType) {
        try{

            byte[] fileData = multipartFile.getBytes();

            Storage storage = createStorage();
            Bucket bucket = storage.get(gcpBucketId,Storage.BucketGetOption.fields());

            UUID uuid = UUID.randomUUID();
            checkFileExtension(originalName);
                    Blob blob = bucket.create(gcpDirectoryName + "/" + uuid.toString() +  "-"+originalName + checkFileExtension(originalName), fileData, contentType);
            if(blob != null){
                System.out.println(blob.getSelfLink());
                return new FileDto(blob.getName(), blob.getMediaLink());
            }

        }catch (Exception e){
           throw new GCSFileException(e.getMessage());
        }
        throw new GCSFileException("구글 스토리지에 파일 업로드중 문제가 발생하였씁니다");
    }

    public void DeleteFile(String fileName) throws IOException {
        Storage storage = createStorage();
        storage.delete(BlobId.of(gcpBucketId, fileName));
    }

    // 서비스 계정 키는 저장소에 포함하지 않고 GCP_CREDENTIALS_PATH 경로의 파일에서 읽는다.
    private Storage createStorage() throws IOException {
        try (InputStream inputStream = new FileInputStream(gcpConfigFile)) {
            StorageOptions options = StorageOptions.newBuilder().setProjectId(gcpProjectId)
                    .setCredentials(GoogleCredentials.fromStream(inputStream)).build();
            return options.getService();
        }
    }


    private String checkFileExtension(String fileName) {
        if(fileName != null && fileName.contains(".")){
            String[] extensionList = {".PNG", ".JPEG", ".JPG"};

            for(String extension: extensionList) {
                if (fileName.toUpperCase().endsWith(extension)) {
                    return extension;
                }
            }
        }
        throw new FileTypeException("이미지 파일이 아닙니다");
    }}
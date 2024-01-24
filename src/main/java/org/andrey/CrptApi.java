package org.andrey;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 */
@Slf4j
public class CrptApi {
    private final Semaphore semaphore;
    private final ScheduledExecutorService resetScheduler;
    private final HttpClient client;
    private static AtomicInteger requestCount = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5); // Ограничение в 5 запроса в минуту
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    Document document = createTestDocument();
                    crptApi.createDocument(document, "testSignature");

                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

    }

    public static int getRequestCount() {
        return requestCount.get();
    }

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

        // Инициализация планировщика для сброса семафора
        this.resetScheduler = Executors.newScheduledThreadPool(1);
        this.resetScheduler.scheduleAtFixedRate(() -> {
            int permitsToRelease = requestLimit - semaphore.availablePermits();
            if (permitsToRelease > 0) {
                semaphore.release(permitsToRelease);
                log.info("Семафор сброшен. Высвобождено разрешений: {}", permitsToRelease);
            } else {
                log.info("Семафор не требует сброса. Доступных разрешений: {}", semaphore.availablePermits());
            }
        }, 0, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        log.info("Попытка отправить документ: {}", document.getDoc_id());
        semaphore.acquire();
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonDocument = mapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("X-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                    .build();

            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            requestCount.incrementAndGet();
            log.info("Документ {} успешно отправлен. Статус ответа: {}", document.getDoc_id(), response.statusCode());
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода при отправке документа {}: {}", document.getDoc_id(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Поток был прерван при отправке документа {}: {}", document.getDoc_id(), e.getMessage());
        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке документа {}: {}", document.getDoc_id(), e.getMessage());
        }
    }
    @Data
    public static class Document {
        private String description;
        private String participantInn;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private Date production_date;
        private String production_type;
        private Date reg_date;
        private String reg_number;
        private List<Product> products;

    }
    @Data
    public class Product {
        private String certificate_document;
        private Date certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private Date production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
   private static Document createTestDocument() {
        Document document = new Document();
        document.setDescription("Описание товара");
        document.setParticipantInn("123456789");
        document.setDoc_id("DOC123456");
        document.setDoc_status("ACTIVE");
        document.setDoc_type("LP_INTRODUCE_GOODS");
        document.setImportRequest(false);
        document.setOwner_inn("987654321");
        document.setParticipant_inn("123456789");
        document.setProducer_inn("555555555");
        document.setProduction_date(new Date());
        document.setProduction_type("Производственный тип");
        document.setReg_date(new Date());
        document.setReg_number("REG123456");
        // Создание списка продуктов
        List<Product> products = new ArrayList<>();
        document.setProducts(products);
        return document;
    }
}

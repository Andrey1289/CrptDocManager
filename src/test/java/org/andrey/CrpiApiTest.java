package org.andrey;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class CrpiApiTest {
    @Test
    public void testRequestLimit() throws InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5); // Допустим 5 запросов в секунду
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) { // Попытка выполнить 10 запросов
            executor.submit(() -> {
                try {
                    CrptApi.Document document = CrptApi.createTestDocument();
                    crptApi.createDocument(document, "testSignature");
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        // Проверяем, что количество выполненных запросов соответствует ожидаемому лимиту
        assertEquals(5, crptApi.getRequestCount());
    }
}
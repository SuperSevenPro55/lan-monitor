package ru.ssp55.lanmonitor.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.System.currentTimeMillis;

@Service
public class NetworkScannerService {
    private final String SUBNET = getLocalSubnet();

    @Scheduled(fixedRate = 60000)
    public void scanNetwork() {
        System.out.println("Сканирование сети: " + SUBNET + "x");
        long startTime = currentTimeMillis();

        try (ExecutorService executor = Executors.newFixedThreadPool(50)) {
            for (int i = 1; i <= 255; i++) {
                final String ipToTest = SUBNET + i;
                executor.submit(() -> {
                    try {
                        InetAddress address = InetAddress.getByName(ipToTest);
                        if (address.isReachable(200)) {
                            System.out.println("Найдено устройство: " + ipToTest);
                        }
                    } catch (Exception ignored) {

                    }
                });
            }

            executor.shutdown();
            try {
                //noinspection ResultOfMethodCallIgnored
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.out);
            }
        } catch (Exception ignored) {

        }

        long endTime = System.currentTimeMillis();

        System.out.println("Сканирование завершено за " + (endTime - startTime) + "мс!");
    }

    public String getLocalSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    if (address.getHostAddress().contains(".")) {
                        String ip = address.getHostAddress();

                        return ip.substring(0, ip.lastIndexOf('.') + 1);
                    }
                }

            }

        } catch (Exception e) {
            System.out.println("Не удалось получить сеть: " + e.getMessage());
        }

        return "192.168.0.";
    }
}

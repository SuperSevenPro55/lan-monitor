package ru.ssp55.lanmonitor.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.currentTimeMillis;

@Service
public class NetworkScannerService {
    private final MessageManager msg;

    public NetworkScannerService(MessageManager msg) {
        this.msg = msg;
    }

    @Scheduled(fixedRate = 30000)
    public void scanNetwork() {
        String localIp = getLocalIpAddress();
        if (localIp == null) {
            System.out.println(msg.get("scanner.no.connection"));
            return;
        }

        String subnet = localIp.substring(0, localIp.lastIndexOf(".") + 1);

        System.out.println(msg.get("scanner.server.ip", localIp));
        System.out.println(msg.get("scanner.start", subnet));

        long startTime = currentTimeMillis();

        try (ExecutorService executor = Executors.newFixedThreadPool(50)) {
            for (int i = 1; i <= 255; i++) {
                final String ipToTest = subnet + i;
                executor.submit(() -> {
                    try {
                        if (ipToTest.equals(localIp)) {
                            return;
                        }

                        InetAddress address = InetAddress.getByName(ipToTest);
                        if (address.isReachable(200)) {
                            String macAddress = getMacFromArpTable(ipToTest);
                            System.out.println(msg.get("scanner.found", ipToTest, macAddress));
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

        System.out.println(msg.get("scanner.finish", endTime - startTime));
    }

    /**
     * Находит IP-адрес устройства, на котором запущен сервер через сканирование интерфейсов, подключенных к устройству
     * @return IP-адрес устройства, на котором запущен сервер
     */
    public String getLocalIpAddress() {
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

                    if (address.getHostAddress().contains(".") && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }

            }

        } catch (Exception e) {
            System.out.println(msg.get("scanner.error.interfaces", e.getMessage()));
        }

        return null;
    }

    /**
     * Запускает процесс в консоли, выводящий ARP-таблицу. Находит там MAC-адрес
     * @param ip IP-адрес, у которого будет находиться MAC-адрес
     * @return MAC-адрес указанного IP-адреса
     */
    private String getMacFromArpTable(String ip) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("arp", "-a", ip);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder systemOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                systemOutput.append(line);
            }
            reader.close();

            Pattern pattern = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
            Matcher matcher = pattern.matcher(systemOutput.toString());

            if (matcher.find()) {
                return matcher.group().replace("-", ":").toUpperCase();
            }

        } catch (Exception e) {
            System.out.println(msg.get("scanner.error.arp.table.read", e.getMessage()));
        }

        return msg.get("scanner.error.arp.table.found");
    }
}

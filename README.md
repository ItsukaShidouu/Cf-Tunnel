# CloudflareTunnel (Paper 1.20.6)

Plugin ini menjalankan proses `cloudflared tunnel run --token ...` sebagai child process server Minecraft. Token hanya berada pada `plugins/CloudflareTunnel/config.yml` dan tidak pernah ditampilkan oleh perintah plugin.

Plugin juga dapat memprovision route TCP dan DNS custom domain melalui perintah `/cftunnel provision`. Perintah ini memperbarui konfigurasi tunnel menjadi `tcp://<origin-host>:<port>` dan membuat/memperbarui CNAME proxied ke `<tunnel-id>.cfargotunnel.com`.

## Batasan Cloudflare yang penting

Cloudflare Zero Trust/Tunnel dapat meneruskan Minecraft sebagai **TCP yang dilindungi Access**, tetapi pemain harus menjalankan `cloudflared` (atau memakai konektivitas private-network/WARP sesuai kebijakan Anda). Pemain tidak bisa sekadar memasukkan `mc.domain-anda.com` ke Minecraft vanilla lalu terhubung langsung. Untuk akses Minecraft publik langsung dengan custom domain, gunakan layanan L4 publik seperti **Cloudflare Spectrum**, atau penyedia TCP tunnel lain.

## Instalasi

1. Gunakan Paper 1.20.6 dengan Java 21.
2. Instal `cloudflared` pada mesin yang sama dengan server Minecraft. Pastikan dapat dipanggil dari terminal (`cloudflared --version`), atau isi path absolutnya di config.
3. Salin **Tunnel token** dari perintah instalasi connector Cloudflare ke `plugins/CloudflareTunnel/config.yml`, pada `tunnel.token`.
4. Isi konfigurasi berikut:

   ```yml
   minecraft:
     hostname: "mc.domain-anda.com"
     origin-host: "127.0.0.1"
     port: 25565
   ```

5. Pilih salah satu cara untuk membuat route:
   - **Otomatis (disarankan):** buat API Token Cloudflare dengan izin *Cloudflare Tunnel: Edit* pada account tunnel dan *DNS: Edit* pada zone domain. Isi `cloudflare-api` (`enabled`, `api-token`, `account-id`, `zone-id`, dan `tunnel-id`), lalu jalankan `/cftunnel provision` sebagai OP.
   - **Manual:** pada **Cloudflare Dashboard → Networking → Tunnels → Routes**, tambahkan *Published application* dengan hostname `mc.domain-anda.com` dan service `tcp://127.0.0.1:25565`. Dashboard otomatis membuat CNAME hostname ke tunnel.
6. Letakkan `CloudflareTunnel.jar` dalam folder `plugins`, nyalakan server, lalu jalankan `/cftunnel start` (atau aktifkan `auto-start`).

`/cftunnel provision` tidak dijalankan otomatis; perintah tersebut memperbarui hanya route dengan hostname yang ada di `minecraft.hostname`, mempertahankan route lain pada tunnel, dan menolak menimpa record DNS non-CNAME pada hostname yang sama.

## Untuk pemain

Setelah diizinkan oleh kebijakan Cloudflare Access, pemain menjalankan perintah berikut pada komputer mereka:

```powershell
cloudflared access tcp --hostname mc.domain-anda.com --url localhost:25565
```

Biarkan perintah itu berjalan, kemudian tambahkan server Minecraft dengan alamat `localhost` (atau `localhost:25565`).

## Perintah

`/cftunnel status`, `/cftunnel start`, `/cftunnel stop`, `/cftunnel restart`, `/cftunnel reload`, `/cftunnel provision`, dan `/cftunnel guide`.

Semua memerlukan permission `cftunnel.admin` (default: OP).

## Build

```powershell
mvn package
```

JAR hasil build: `target/CloudflareTunnel.jar`.

## Operasional

Untuk produksi, menjalankan `cloudflared` sebagai service sistem biasanya lebih tahan restart daripada child process plugin. Plugin ini disediakan ketika Anda memang ingin lifecycle tunnel mengikuti server Minecraft.

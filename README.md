# MineGens Auto Upgrade (Fabric Client Mod)

Mod client Fabric Minecraft yang didesain khusus untuk server MineGens. Mod ini mengotomatiskan perintah `/upgradegen`, menyembunyikan GUI panel agar tidak muncul di layar client, dan menekan **Slot ID 41** (Upgrade All) secara otomatis.

---

## ⚡ Fitur Utama

- 🚀 **Auto Command `/upgradegen`**: Cukup tekan tombol hotkey (default: **`U`**) untuk otomatis mengirim perintah `/upgradegen`.
- 🙈 **Hidden Panel GUI**: Menyembunyikan GUI/panel upgrade di client saat container terbuka, sehingga GUI tidak menggangu layar dan proses upgrade berlangsung sekejap di background.
- 🎯 **Auto Click Slot ID 41**: Mengirim packet klik otomatis pada slot ID 41 (**Upgrade All**) begitu container upgrade terbuka.
- ⚙️ **In-Game Mod Menu Settings**: Terintegrasi penuh dengan **Mod Menu**. Menyediakan toggle ON/OFF, opsi sembunyikan panel, penyesuaian Slot ID, dan perintah kustom langsung dari menu game.
- ⌨️ **Quick Hotkeys**:
  - `U` - Menjalankan Auto Upgrade (`/upgradegen` + Slot 41 click)
  - `O` - Toggle status mod ON / OFF dengan cepat in-game

---

## 🛠️ Cara Install & Menggunakan

1. **Persyaratan**:
   - Fabric Loader (versi 0.15.0+)
   - Minecraft 1.20.4 (atau versi Fabric yang sesuai)
   - Mod Menu (Opsional, untuk mengakses menu setting in-game)
   - Fabric API

2. **Pemasangan**:
   - Salin file `.jar` hasil kompilasi dari folder `build/libs/` ke folder `.minecraft/mods/`.
   - Jalankan Minecraft menggunakan profil Fabric.

3. **Pengaturan In-Game**:
   - Buka **Mod Menu** -> Cari **MineGens Auto Upgrade** -> Buka **Settings**.
   - Anda dapat mengatur:
     - **Mod Status**: ENABLED / DISABLED
     - **Hide GUI Panel**: ON / OFF
     - **Auto Close GUI**: ON / OFF
     - **Target Slot ID**: Default `41` (Bisa diubah jika layout server berubah)
     - **Auto Command**: Default `/upgradegen`

---

## 📦 Cara Build dari Source Code

Pastikan Java 17 / 21 sudah terinstall di komputer Anda:

```bash
# Di terminal / CMD:
gradlew.bat build
```

File `.jar` akan terbuat di folder `build/libs/minegens-auto-upgrade-1.0.0.jar`.

---

## 📄 Struktur Kode Utama

- `com.minegens.autoupgrade.MineGensAutoUpgrade` - Main Mod Initializer
- `com.minegens.autoupgrade.client.MineGensAutoUpgradeClient` - Client Initializer & Hotkey Listener
- `com.minegens.autoupgrade.logic.AutoUpgradeHandler` - Penanganan klik Slot ID 41 & auto-close container
- `com.minegens.autoupgrade.mixin.MinecraftClientMixin` - Interseptor tampilan Screen/GUI (Hidden mode)
- `com.minegens.autoupgrade.config.ConfigScreen` - GUI Pengaturan In-game
- `com.minegens.autoupgrade.config.ModMenuIntegration` - Integration dengan Mod Menu API

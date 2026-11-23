package br.com.scannertelagem;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 100;
    private static final int SAVE_REPORT = 101;
    private Uri selectedTree;
    private TextView status;
    private final List<String> findings = new ArrayList<>();
    private int scannedFiles = 0;
    private int scannedFolders = 0;
    private int inaccessible = 0;
    private String reportText = "";
    private static final String[] TERMS = {"delta", "fluxus", "hydrogen", "arceus", "codex", "evon", "vegax", "trigon", "cryptic", "executor"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        status.setText("Pronto. Escolha uma pasta autorizada para começar.\n\nA análise considera nomes e metadados; não lê conversas, senhas ou dados privados de outros aplicativos.");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 42, 36, 36);
        root.setBackgroundColor(0xFF101827);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("SCANNER DE TELAGEM COMPLETO");
        title.setTextColor(0xFFFFFFFF); title.setTextSize(22); title.setGravity(Gravity.CENTER);
        title.setPadding(0, 8, 0, 12);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Análise local e autorizada • Android 10+");
        subtitle.setTextColor(0xFFB8C4D8); subtitle.setTextSize(14); subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        addButton(root, "1. Escolher pasta autorizada", v -> pickFolder());
        addButton(root, "2. Analisar pasta selecionada", v -> startScan());
        addButton(root, "3. Ver aplicativos instalados", v -> listApps());
        addButton(root, "4. Exportar relatório", v -> exportReport());
        status = new TextView(this);
        status.setTextColor(0xFFE5EAF2); status.setTextSize(14);
        status.setPadding(4, 24, 4, 20);
        root.addView(status);
        TextView foot = new TextView(this);
        foot.setText("Importante: resultados por nome são apenas indícios, não comprovam uso de executor. O Android pode bloquear determinadas pastas. Nenhuma proteção é contornada.");
        foot.setTextColor(0xFF9EABC0); foot.setTextSize(12); foot.setPadding(4, 24, 4, 8);
        root.addView(foot);
        setContentView(scroll);
    }

    private void addButton(LinearLayout root, String label, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 14, 0, 0);
        root.addView(b, lp); b.setOnClickListener(action);
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK_FOLDER) {
            selectedTree = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(selectedTree, flags); } catch (Exception ignored) {}
            status.setText("Pasta autorizada:\n" + selectedTree + "\n\nAgora toque em ‘Analisar pasta selecionada’.");
        } else if (requestCode == SAVE_REPORT) {
            try {
                getContentResolver().openOutputStream(data.getData()).write(reportText.getBytes("UTF-8"));
                Toast.makeText(this, "Relatório exportado.", Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(this, "Não foi possível salvar: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    private void startScan() {
        if (selectedTree == null) { Toast.makeText(this, "Escolha uma pasta primeiro.", Toast.LENGTH_SHORT).show(); return; }
        status.setText("Analisando... aguarde.");
        new Thread(() -> {
            findings.clear(); scannedFiles = scannedFolders = inaccessible = 0;
            DocumentFile root = DocumentFile.fromTreeUri(this, selectedTree);
            if (root != null) scanNode(root, 0);
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            StringBuilder out = new StringBuilder();
            out.append("SCANNER DE TELAGEM COMPLETO\nData: ").append(now).append("\nPasta: ").append(selectedTree).append("\nArquivos examinados: ").append(scannedFiles).append("\nPastas examinadas: ").append(scannedFolders).append("\nFalhas de acesso: ").append(inaccessible).append("\nIndícios por nome: ").append(findings.size()).append("\n\n");
            if (findings.isEmpty()) out.append("Nenhum nome correspondente foi encontrado na pasta autorizada.\n");
            else for (String f : findings) out.append(f).append("\n");
            out.append("\nAVISO: correspondência de nome é apenas um indício, não prova instalação ou uso. Pastas não autorizadas ou bloqueadas não foram analisadas.\n");
            reportText = out.toString();
            runOnUiThread(() -> status.setText(reportText));
        }).start();
    }

    private void scanNode(DocumentFile node, int depth) {
        if (depth > 20) return;
        try {
            if (node.isDirectory()) {
                scannedFolders++;
                DocumentFile[] children = node.listFiles();
                for (DocumentFile child : children) scanNode(child, depth + 1);
            } else if (node.isFile()) {
                scannedFiles++;
                String name = node.getName() == null ? "(sem nome)" : node.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                for (String term : TERMS) {
                    if (lower.contains(term)) {
                        String hash = "não calculado";
                        if (node.length() >= 0 && node.length() <= 50L * 1024L * 1024L) hash = sha256(node);
                        findings.add("POSSÍVEL INDÍCIO: " + name + " | tamanho=" + node.length() + " bytes | SHA-256=" + hash + " | URI=" + node.getUri());
                        break;
                    }
                }
            }
        } catch (Exception e) { inaccessible++; }
    }

    private String sha256(DocumentFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = getContentResolver().openInputStream(file.getUri());
            if (in == null) return "indisponível";
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            StringBuilder s = new StringBuilder(); for (byte b : md.digest()) s.append(String.format(Locale.ROOT, "%02x", b));
            return s.toString();
        } catch (Exception e) { return "indisponível"; }
    }

    private void listApps() {
        new Thread(() -> {
            List<String> apps = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(0);
                for (PackageInfo p : packages) {
                    String label = p.applicationInfo == null ? p.packageName : pm.getApplicationLabel(p.applicationInfo).toString();
                    apps.add(label + " | " + p.packageName);
                }
            } catch (Exception e) { apps.add("Falha ao consultar aplicativos: " + e.getMessage()); }
            Collections.sort(apps, String.CASE_INSENSITIVE_ORDER);
            StringBuilder b = new StringBuilder("APLICATIVOS VISÍVEIS AO SCANNER (" + apps.size() + ")\n\n");
            for (String a : apps) b.append(a).append('\n');
            b.append("\nA lista depende das regras de visibilidade de pacotes do Android. Nenhum dado privado dos aplicativos é acessado.\n");
            reportText = b.toString();
            runOnUiThread(() -> status.setText(reportText));
        }).start();
    }

    private void exportReport() {
        if (reportText == null || reportText.trim().isEmpty()) { Toast.makeText(this, "Faça uma análise ou consulte os aplicativos primeiro.", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "relatorio_scanner_telagem.txt");
        startActivityForResult(i, SAVE_REPORT);
    }
}

package com.botoni.avaliacaodepreco.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.graphics.pdf.PdfDocument.PageInfo;
import android.graphics.pdf.PdfDocument.Page;
import android.os.Environment;

public class PdfReport {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 40;

    private String categoriaAnimal;
    private double precoArroba;
    private double percentualAgio;
    private double pesoAnimal;
    private int quantidadeAnimais;
    private double valorPorCabeca;
    private double valorPorKg;
    private double valorTotal;
    private String origem;
    private String destino;
    private double distancia;
    private double valorFrete;
    private double valorFinalTotal;
    private double valorFinalPorKg;

    private final DecimalFormat dfMoeda = new DecimalFormat("R$ #,##0.00");
    private final DecimalFormat dfDecimal = new DecimalFormat("#,##0.00");
    private final DecimalFormat dfPercentual = new DecimalFormat("#,##0.00%");
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    private PdfReport(Builder builder) {
        this.categoriaAnimal = builder.categoriaAnimal;
        this.precoArroba = builder.precoArroba;
        this.percentualAgio = builder.percentualAgio;
        this.pesoAnimal = builder.pesoAnimal;
        this.quantidadeAnimais = builder.quantidadeAnimais;
        this.valorPorCabeca = builder.valorPorCabeca;
        this.valorPorKg = builder.valorPorKg;
        this.valorTotal = builder.valorTotal;
        this.origem = builder.origem;
        this.destino = builder.destino;
        this.distancia = builder.distancia;
        this.valorFrete = builder.valorFrete;
        this.valorFinalTotal = builder.valorFinalTotal;
        this.valorFinalPorKg = builder.valorFinalPorKg;
    }


    public File create(Context context) throws IOException {
        PdfDocument document = new PdfDocument();
        PageInfo pageInfo = new PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        content(canvas);
        document.finishPage(page);
        return save(context, document);
    }

    private File save(Context context, PdfDocument document) throws IOException {
        File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (externalFilesDir == null) {
            externalFilesDir = context.getFilesDir();
        }

        File file = new File(externalFilesDir, "report_" + System.currentTimeMillis() + ".pdf");
        FileOutputStream outputStream = new FileOutputStream(file);
        document.writeTo(outputStream);
        outputStream.close();
        document.close();
        return file;
    }

    private void content(Canvas canvas) {
        Paint paint = new Paint();
        int yPos = MARGIN;

        paint.setTextSize(24);
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        canvas.drawText("RELATÓRIO DE NEGOCIAÇÃO", MARGIN, yPos, paint);
        yPos += 40;

        paint.setTextSize(10);
        paint.setFakeBoldText(false);
        paint.setColor(Color.GRAY);
        canvas.drawText("Gerado em: " + sdf.format(new Date()), MARGIN, yPos, paint);
        yPos += 30;

        paint.setColor(Color.DKGRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, paint);
        yPos += 25;

        yPos = session(canvas, "DADOS DO ANIMAL", yPos);
        yPos = line(canvas, "Categoria:", categoriaAnimal, yPos);
        yPos = line(canvas, "Peso por Animal:", dfDecimal.format(pesoAnimal) + " kg", yPos);
        yPos = line(canvas, "Quantidade:", String.valueOf(quantidadeAnimais) + " cabeças", yPos);
        yPos += 15;

        yPos = session(canvas, "VALORES BASE", yPos);
        yPos = line(canvas, "Preço da Arroba:", dfMoeda.format(precoArroba), yPos);
        yPos = line(canvas, "Percentual de Ágio:", dfPercentual.format(percentualAgio / 100), yPos);
        yPos = line(canvas, "Valor por Cabeça:", dfMoeda.format(valorPorCabeca), yPos);
        yPos = line(canvas, "Valor por Kg:", dfMoeda.format(valorPorKg), yPos);
        yPos += 15;

        yPos = session(canvas, "VALORES TOTAIS", yPos);
        yPos = line(canvas, "Valor Total (s/ frete):", dfMoeda.format(valorTotal), yPos);
        yPos += 15;

        yPos = session(canvas, "TRANSPORTE", yPos);
        yPos = line(canvas, "Origem:", origem, yPos);
        yPos = line(canvas, "Destino:", destino, yPos);
        yPos = line(canvas, "Distância:", dfDecimal.format(distancia) + " km", yPos);
        yPos = line(canvas, "Valor do Frete:", dfMoeda.format(valorFrete), yPos);
        yPos += 15;

        paint.setColor(Color.DKGRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, paint);
        yPos += 25;

        paint.setTextSize(16);
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        canvas.drawText("VALOR FINAL TOTAL:", MARGIN, yPos, paint);
        paint.setColor(Color.rgb(0, 128, 0));
        canvas.drawText(dfMoeda.format(valorFinalTotal), 300, yPos, paint);
        yPos += 30;

        paint.setTextSize(14);
        paint.setColor(Color.BLACK);
        canvas.drawText("Valor Final por Kg:", MARGIN, yPos, paint);
        paint.setColor(Color.rgb(0, 128, 0));
        canvas.drawText(dfMoeda.format(valorFinalPorKg), 300, yPos, paint);

        paint.setTextSize(9);
        paint.setColor(Color.GRAY);
        paint.setFakeBoldText(false);
        canvas.drawText("Documento gerado automaticamente", MARGIN, PAGE_HEIGHT - 30, paint);
    }

    private int session(Canvas canvas, String titulo, int yPos) {
        Paint paint = new Paint();

        paint.setColor(Color.LTGRAY);
        canvas.drawRect(MARGIN, yPos - 15, PAGE_WIDTH - MARGIN, yPos + 5, paint);

        paint.setTextSize(14);
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        canvas.drawText(titulo, MARGIN + 5, yPos, paint);

        return yPos + 25;
    }

    private int line(Canvas canvas, String label, String valor, int yPos) {
        Paint paint = new Paint();
        paint.setTextSize(12);
        paint.setColor(Color.BLACK);

        paint.setFakeBoldText(true);
        canvas.drawText(label, MARGIN + 10, yPos, paint);

        paint.setFakeBoldText(false);
        canvas.drawText(valor, 250, yPos, paint);

        return yPos + 20;
    }


    public static class Builder {
        private String categoriaAnimal;
        private double precoArroba;
        private double percentualAgio;
        private double pesoAnimal;
        private int quantidadeAnimais;
        private double valorPorCabeca;
        private double valorPorKg;
        private double valorTotal;
        private String origem;
        private String destino;
        private double distancia;
        private double valorFrete;
        private double valorFinalTotal;
        private double valorFinalPorKg;

        public Builder categoriaAnimal(String categoriaAnimal) {
            this.categoriaAnimal = categoriaAnimal;
            return this;
        }

        public Builder precoArroba(double precoArroba) {
            this.precoArroba = precoArroba;
            return this;
        }

        public Builder percentualAgio(double percentualAgio) {
            this.percentualAgio = percentualAgio;
            return this;
        }

        public Builder pesoAnimal(double pesoAnimal) {
            this.pesoAnimal = pesoAnimal;
            return this;
        }

        public Builder quantidadeAnimais(int quantidadeAnimais) {
            this.quantidadeAnimais = quantidadeAnimais;
            return this;
        }

        public Builder valorPorCabeca(double valorPorCabeca) {
            this.valorPorCabeca = valorPorCabeca;
            return this;
        }

        public Builder valorPorKg(double valorPorKg) {
            this.valorPorKg = valorPorKg;
            return this;
        }

        public Builder valorTotal(double valorTotal) {
            this.valorTotal = valorTotal;
            return this;
        }

        public Builder origem(String origem) {
            this.origem = origem;
            return this;
        }

        public Builder destino(String destino) {
            this.destino = destino;
            return this;
        }

        public Builder distancia(double distancia) {
            this.distancia = distancia;
            return this;
        }

        public Builder valorFrete(double valorFrete) {
            this.valorFrete = valorFrete;
            return this;
        }

        public Builder valorFinalTotal(double valorFinalTotal) {
            this.valorFinalTotal = valorFinalTotal;
            return this;
        }

        public Builder valorFinalPorKg(double valorFinalPorKg) {
            this.valorFinalPorKg = valorFinalPorKg;
            return this;
        }

        public PdfReport build() {
            return new PdfReport(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

package com.huangder.lumibooks.pdfconversion;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class PdfTextExtractor_Factory implements Factory<PdfTextExtractor> {
  @Override
  public PdfTextExtractor get() {
    return newInstance();
  }

  public static PdfTextExtractor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PdfTextExtractor newInstance() {
    return new PdfTextExtractor();
  }

  private static final class InstanceHolder {
    private static final PdfTextExtractor_Factory INSTANCE = new PdfTextExtractor_Factory();
  }
}

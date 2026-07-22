package com.huangder.lumibooks.data.repository;

import com.huangder.lumibooks.data.local.dao.TagDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class TagRepositoryImpl_Factory implements Factory<TagRepositoryImpl> {
  private final Provider<TagDao> tagDaoProvider;

  private TagRepositoryImpl_Factory(Provider<TagDao> tagDaoProvider) {
    this.tagDaoProvider = tagDaoProvider;
  }

  @Override
  public TagRepositoryImpl get() {
    return newInstance(tagDaoProvider.get());
  }

  public static TagRepositoryImpl_Factory create(Provider<TagDao> tagDaoProvider) {
    return new TagRepositoryImpl_Factory(tagDaoProvider);
  }

  public static TagRepositoryImpl newInstance(TagDao tagDao) {
    return new TagRepositoryImpl(tagDao);
  }
}

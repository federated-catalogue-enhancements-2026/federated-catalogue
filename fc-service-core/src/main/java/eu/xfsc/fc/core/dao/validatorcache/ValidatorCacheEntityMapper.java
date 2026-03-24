package eu.xfsc.fc.core.dao.validatorcache;

import eu.xfsc.fc.core.pojo.Validator;

public final class ValidatorCacheEntityMapper {

  private ValidatorCacheEntityMapper() {
  }

  public static ValidatorCacheEntity toEntity(Validator validator) {
    if (validator == null) {
      return null;
    }
    return new ValidatorCacheEntity(
        validator.getDidURI(),
        validator.getPublicKey(),
        validator.getExpirationDate());
  }

  public static Validator toValidator(ValidatorCacheEntity entity) {
    if (entity == null) {
      return null;
    }
    return new Validator(
        entity.getDiduri(),
        entity.getPublickey(),
        entity.getExpirationtime());
  }
}

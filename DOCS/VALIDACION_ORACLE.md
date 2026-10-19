# Validación reproducible en Oracle (pendiente de ejecución)

Objetivo: Oracle 19c o posterior, SQLcl/SQL*Plus y un esquema vacío. No usar una base productiva para estas pruebas. El entorno de desarrollo actual no dispone de servidor Oracle; los resultados H2 no se presentan como resultados Oracle.

## Compilación y escenarios del PDF

Desde db/oracle ejecutar `@demo_install.sql`. El instalador crea el modelo, carga muestras, migra soporte, compila procedimiento/trigger, revisa USER_ERRORS y ejecuta `06_verify_procedure.sql`. Esperar `PASS: Oracle sample cases and retries; changes rolled back`. Una aserción o error SQL termina el proceso con código de error. Guardar salida y versión Oracle.

`@02_detect_anomalies.sql` debe mostrar AX91 (estado), BX17 (178000 de neto, no 356000) y CQ22 (contradicción), excluyendo DK44. Crear datos viejos con un evento reciente y verificar que el neto incluye todo el historial, como en AnomalyQueryTest.

## Concurrencia con dos sesiones

Sobre el demo original, en sesión A ejecutar reconcile_payment para PAY-BX17 sin COMMIT. En sesión B invocar el mismo pago antes de cinco segundos. Confirmar A antes de agotar la espera: B debe terminar ALREADY_CONSISTENT. Hacer COMMIT B y verificar tres movimientos, un reverso a 5003, una auditoría y row_version=1. Reiniciar con otro pago de prueba para repetir agotando el WAIT: B debe fallar sin escrituras. Cerrar siempre ambas transacciones.

Repetir con dos peticiones HTTP concurrentes en perfil oracle, verificando conteos. Luego probar una ejecución Java simultánea con otra PL/SQL: ambas comparten el bloqueo del padre.

## Paridad de auditoría entre Java y PL/SQL

Sobre CQ22 original: llamar por HTTP, verificar una auditoría MANUAL_REVIEW. Invocar procedimiento y COMMIT: debe seguir existiendo una sola auditoría con el mismo evidence_key. Repetir primero por PL/SQL y después por Java sobre otro pago contradictorio. Una nueva fila de evento debe habilitar otra auditoría, aunque el resultado continúe siendo MANUAL_REVIEW.

## Fallo y rollback

En laboratorio crear un trigger temporal BEFORE INSERT sobre SUPPORT_AUDIT que lance RAISE_APPLICATION_ERROR. Conciliar un nuevo pago con dos débitos mediante Java: debe fallar con respuesta sanitizada y conservar sus dos débitos, estado y versión originales. Repetir por PL/SQL; comprobar que el procedimiento vuelve a su SAVEPOINT y propaga el error. Retirar el trigger de prueba. No reutilizar IDs fijos del demo en un esquema compartido.

## Inmutabilidad y bordes

- Intentar UPDATE o DELETE de ledger: error -20001, sin cambios.
- Mismo related_entry_id en otro reverso: rechazar por índice único.
- Reverso del mismo importe apuntando a otro pago: MANUAL_REVIEW, no compensar.
- Débito parcial, sin eventos, DECLINED con historial y aprobación completamente revertida: revisión.
- Solo DECLINED sin ledger: REJECTED, repetición sin auditoría adicional.
- Pago inexistente: NOT_FOUND en PL/SQL y 404 en API.
- Ajustar NLS_NUMERIC_CHARACTERS a `,.` y repetir paridad de huella; importes canónicos deben seguir usando punto y dos decimales.

Registrar comandos, resultados y cualquier divergencia antes de afirmar compatibilidad certificada con Oracle.

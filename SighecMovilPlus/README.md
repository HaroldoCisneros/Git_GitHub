# SighecMovilPlus

App Android en **Kotlin** para administrar negocios pequeños. **Toda la información se guarda localmente en el teléfono** (base de datos SQLite con Room); no usa servidor ni internet.

## Tecnología

| Parte | Librería |
|---|---|
| Interfaz | Jetpack Compose + Material 3 |
| Base de datos local | Room (SQLite) |
| Navegación | Navigation Compose |
| Huella digital | androidx.biometric |
| Contraseñas | PBKDF2-SHA256 con salt (no se guardan en texto plano) |

Requisitos: Android 8.0 (API 26) o superior. Para compilar: Android Studio (Ladybug o más reciente), JDK 17.

## Cómo abrirla

1. En Android Studio: **File → Open** y selecciona la carpeta `SighecMovilPlus`.
2. Espera a que Gradle sincronice y presiona **Run ▶**.
3. La primera vez la app pide crear el **usuario administrador**.

## Módulos

### 1. Acceso y seguridad
- **Login con usuario y contraseña.**
- **Login con huella**: desde la pantalla de inicio (ícono de huella) el usuario liga la huella del teléfono a su usuario. En el login aparece el botón "Entrar con huella".
- **Maestro de usuarios** (como en Sighec Móvil): el administrador da de alta usuarios y marca **qué opciones ve cada uno**. El menú principal solo muestra las opciones permitidas y las pantallas validan el permiso. También puede activar/desactivar el usuario y permitir o no la huella.
- **Bitácora**: registra inicios de sesión (incluye intentos fallidos), altas, cambios, bajas, facturas, cobros, compras, pagos y movimientos de caja/bancos, con usuario y fecha.

### 2. Catálogos y operaciones

| Grupo | Opción | Qué hace |
|---|---|---|
| Ventas | Clientes | Código, nombre, RFC, contacto, condición de pago y límite de crédito |
| | Facturación | Factura con varios artículos, IVA por artículo, folio consecutivo; descuenta inventario. Se puede cancelar si no tiene cobros (regresa inventario) |
| | Cobros | Aplica pagos a facturas. Efectivo → entra a **Caja**; transferencia/cheque/tarjeta → depósito en la **cuenta bancaria** elegida |
| | Cuentas por cobrar | Saldo por cliente, vencido / por vencer y detalle de facturas pendientes |
| Compras | Proveedores | Igual que clientes |
| | Factura de compras | Registro de la factura del proveedor; suma inventario y actualiza el costo del artículo |
| | Pagos | Aplica pagos a compras. Efectivo → sale de **Caja**; otros → retiro de la cuenta bancaria |
| | Cuentas por pagar | Saldo por proveedor, vencido / por vencer |
| Tesorería | Caja | Saldo, ingresos y egresos manuales, filtro Hoy / Mes / Todo |
| | Bancos | Catálogo de bancos |
| | Cuentas bancarias | Cuentas por banco con saldo inicial, depósitos, retiros y estado de cuenta |
| Catálogos | Artículos | Código, descripción, unidad, precio, costo, % impuesto y existencia |
| | Condiciones de pago | Contado / crédito a N días (se crean Contado, 15 y 30 días de inicio) |

## Estructura del código

```
app/src/main/java/com/sighec/movilplus/
├── data/            Entidades, DAOs, base de datos y Repositorio (reglas de negocio)
├── seguridad/       Módulos/permisos, sesión, hash de contraseñas, huella
└── ui/
    ├── acceso/      Login y configuración inicial
    ├── inicio/      Menú principal con indicadores
    ├── usuarios/    Maestro de usuarios
    ├── catalogos/   Artículos, clientes, proveedores, condiciones, bancos
    ├── ventas/      Facturación, cobros, CxC
    ├── compras/     Compras, pagos, CxP
    ├── tesoreria/   Caja y cuentas bancarias
    ├── bitacora/    Consulta de bitácora
    └── componentes/ Controles reutilizables
```

Todas las operaciones que mueven saldos, inventario, caja o bancos están en `data/Repositorio.kt` dentro de transacciones, para que nunca queden a medias.

## Respaldo

Los datos están excluidos del respaldo en la nube de Android (`data_extraction_rules.xml`) para que la información del negocio no salga del teléfono. Si se desinstala la app se pierden los datos; un respaldo/exportación local es un buen siguiente paso.

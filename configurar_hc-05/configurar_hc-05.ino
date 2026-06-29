#include <SoftwareSerial.h>

// Arduino RX, TX
// Pin 10 recibe desde TXD del HC-05
// Pin 11 envía hacia RXD del HC-05
SoftwareSerial BT(10, 11);

const int LED = 13;
const int BTKEY = 12;

// Configuración del módulo
const char nombreBT[] = "Brazalete-01";
const char pinBT[] = "2004";

void enviarComando(String comando, int espera = 1000) {
  Serial.print("Enviando: ");
  Serial.println(comando);

  BT.print(comando);
  BT.print("\r\n");

  delay(espera);

  Serial.println("Respuesta:");
  while (BT.available()) {
    Serial.write(BT.read());
  }

  Serial.println();
  Serial.println("-------------------------");
}

void setup() {
  pinMode(LED, OUTPUT);
  pinMode(BTKEY, OUTPUT);

  Serial.begin(9600);

  Serial.println("Configurador HC-05 iniciado");
  Serial.println("Preparando modo AT...");

  // KEY/EN en HIGH para modo AT
  digitalWrite(BTKEY, HIGH);

  // Velocidad típica del HC-05 en modo AT completo
  BT.begin(38400);

  digitalWrite(LED, HIGH);
  delay(3000);
  digitalWrite(LED, LOW);

  Serial.println("Enviando comandos AT...");

  // Prueba comunicación
  enviarComando("AT");

  // Limpia dispositivos previamente emparejados
  enviarComando("AT+RMAAD", 1500);

  // Configura nombre visible
  enviarComando(String("AT+NAME=") + nombreBT);

  // Configura PIN
  enviarComando(String("AT+PSWD=") + pinBT);

  // Configura velocidad normal: 9600, sin paridad, 1 stop bit
  enviarComando("AT+UART=9600,0,0");

  // Modo Slave: permite que el celular se conecte al HC-05
  enviarComando("AT+ROLE=0");

  // Permite conexión desde cualquier dispositivo
  enviarComando("AT+CMODE=1");

  // Consulta configuración final
  enviarComando("AT+NAME?");
  enviarComando("AT+PSWD?");
  enviarComando("AT+UART?");
  enviarComando("AT+ROLE?");
  enviarComando("AT+CMODE?");

  // Reinicia módulo
  enviarComando("AT+RESET", 1500);

  digitalWrite(BTKEY, LOW);
  digitalWrite(LED, HIGH);

  Serial.println("Configuracion terminada.");
  Serial.println("HC-05 configurado como SLAVE a 9600 baudios.");
  Serial.println("Ahora apaga y enciende el modulo.");
  Serial.println("Luego carga el codigo normal del brazalete.");
}

void loop() {
  // Puente manual para probar comandos AT desde el Monitor Serial
  if (Serial.available()) {
    BT.write(Serial.read());
  }

  if (BT.available()) {
    Serial.write(BT.read());
  }
}
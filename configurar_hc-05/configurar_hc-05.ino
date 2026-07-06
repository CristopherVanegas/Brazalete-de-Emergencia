#include <SoftwareSerial.h>

// Arduino RX, TX
// Pin 10 recibe desde TXD del HC-05
// Pin 11 envía hacia RXD del HC-05
SoftwareSerial BT(10, 11);

const int LED = 13;
const int BTKEY = 12;

const char nombreBT[] = "Brazalete-01";
const char pinBT[] = "2004";

void enviarComando(String comando, int espera = 1000) {
  Serial.print("Enviando: ");
  Serial.println(comando);

  BT.print(comando);
  BT.print("\r\n");

  unsigned long inicio = millis();
  bool huboRespuesta = false;

  Serial.println("Respuesta:");

  while (millis() - inicio < espera) {
    while (BT.available()) {
      char c = BT.read();
      Serial.write(c);
      huboRespuesta = true;
    }
  }

  if (!huboRespuesta) {
    Serial.print("[SIN RESPUESTA]");
  }

  Serial.println();
  Serial.println("-------------------------");
}

void setup() {
  pinMode(LED, OUTPUT);
  pinMode(BTKEY, OUTPUT);

  Serial.begin(9600);
  delay(1000);

  Serial.println("Configurador HC-05 iniciado");
  Serial.println("Preparando modo AT...");

  digitalWrite(BTKEY, HIGH);

  // Modo AT completo del HC-05
  BT.begin(38400);
  BT.listen();

  digitalWrite(LED, HIGH);
  delay(3000);
  digitalWrite(LED, LOW);

  Serial.println("Enviando comandos AT...");

  // Prueba comunicación
  enviarComando("AT", 1000);

  // Ver versión del módulo
  enviarComando("AT+VERSION?", 1000);

  // Configura nombre visible
  enviarComando(String("AT+NAME=") + nombreBT, 1000);

  // Configura PIN
  enviarComando(String("AT+PSWD=") + pinBT, 1000);

  // Si AT+PSWD no responde OK, prueba luego con AT+PIN
  // enviarComando(String("AT+PIN=") + pinBT, 1000);

  // Modo Slave
  enviarComando("AT+ROLE=0", 1000);

  // Permite conexión desde cualquier dispositivo
  enviarComando("AT+CMODE=1", 1000);

  // Consultas antes de cambiar UART
  enviarComando("AT+NAME?", 1000);
  enviarComando("AT+PSWD?", 1000);
  enviarComando("AT+ROLE?", 1000);
  enviarComando("AT+CMODE?", 1000);

  // Deja UART para el final
  enviarComando("AT+UART=9600,0,0", 1000);

  // NO AT+RESET

  digitalWrite(BTKEY, LOW);
  digitalWrite(LED, HIGH);

  Serial.println("Configuracion terminada.");
  Serial.println("Ahora apaga y enciende manualmente el HC-05.");
  Serial.println("Luego carga el codigo normal usando BT.begin(9600).");
}

void loop() {
  if (Serial.available()) {
    BT.write(Serial.read());
  }

  if (BT.available()) {
    Serial.write(BT.read());
  }
}
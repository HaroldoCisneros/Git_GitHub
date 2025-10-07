<?php
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $nombre = $_POST['nombre'];

    // Conexión a SQL Server
    $serverName = "OFICINA-PC";
    $connectionOptions = array(
        "Database" => "VALPA_A",
        "Uid" => "profit",
        "PWD" => "profit"
    );

    $conn = sqlsrv_connect($serverName, $connectionOptions);

    if ($conn === false) {
        die(print_r(sqlsrv_errors(), true));
    }  
}
?>